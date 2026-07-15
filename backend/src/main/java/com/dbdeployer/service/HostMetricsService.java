package com.dbdeployer.service;

import com.dbdeployer.api.dto.HostMetricsResponse;
import com.dbdeployer.api.dto.HostMetricsResponse.CpuMetrics;
import com.dbdeployer.api.dto.HostMetricsResponse.GpuMetrics;
import com.dbdeployer.api.dto.HostMetricsResponse.HostInfo;
import com.dbdeployer.api.dto.HostMetricsResponse.MemoryMetrics;
import com.dbdeployer.api.dto.HostMetricsResponse.SensorMetrics;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.Sensors;
import oshi.software.os.OperatingSystem;

/**
 * Cross-platform host hardware metrics via <a href="https://github.com/oshi/oshi">OSHI</a>,
 * augmented with best-effort GPU utilization:
 *
 * <ul>
 *   <li><b>All platforms:</b> CPU total + per-core load (tick deltas between calls), memory, swap,
 *       load average, CPU frequency, process/thread counts, sensors (temp / fans / voltage).
 *   <li><b>NVIDIA (Linux/Windows):</b> utilization, VRAM use and temperature via {@code nvidia-smi}
 *       when present.
 *   <li><b>macOS:</b> GPU "Device Utilization %" parsed from {@code ioreg -c IOAccelerator} (works
 *       for Apple Silicon and most Intel-era GPUs, no sudo required).
 * </ul>
 *
 * <p>The service is stateful: CPU load is computed from the tick delta since the previous snapshot,
 * so the first call after startup reports load since boot. Sensor values of {@code 0} are treated
 * as "unsupported" and returned as {@code null}.
 */
@Slf4j
@Service
public class HostMetricsService {

  private static final Pattern IOREG_UTILIZATION =
      Pattern.compile("\"Device Utilization %\"\\s*=\\s*(\\d+)");

  private final SystemInfo systemInfo = new SystemInfo();
  private final CentralProcessor processor;
  private final OperatingSystem os;
  private final HardwareAbstractionLayer hardware;

  /** Previous CPU tick snapshots — guarded by {@code this}. */
  private long[] prevTicks;

  private long[][] prevPerCoreTicks;

  /** Lazily resolved once: whether {@code nvidia-smi} is on the PATH. */
  private volatile Boolean nvidiaSmiAvailable;

  public HostMetricsService() {
    this.hardware = systemInfo.getHardware();
    this.processor = hardware.getProcessor();
    this.os = systemInfo.getOperatingSystem();
    this.prevTicks = processor.getSystemCpuLoadTicks();
    this.prevPerCoreTicks = processor.getProcessorCpuLoadTicks();
  }

  /** One point-in-time sample of every metric the System Health page renders. */
  public synchronized HostMetricsResponse snapshot() {
    // ── CPU load between this call and the previous one ───────────────────
    double totalLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
    double[] perCore = processor.getProcessorCpuLoadBetweenTicks(prevPerCoreTicks);
    prevTicks = processor.getSystemCpuLoadTicks();
    prevPerCoreTicks = processor.getProcessorCpuLoadTicks();

    List<Double> perCorePct = new ArrayList<>(perCore.length);
    for (double c : perCore) {
      perCorePct.add(round1(c * 100.0));
    }

    double[] loadAvg = processor.getSystemLoadAverage(3);
    List<Double> loadAverage =
        Arrays.stream(loadAvg).boxed().map(HostMetricsService::round2).toList();

    CpuMetrics cpu =
        new CpuMetrics(
            processor.getProcessorIdentifier().getName().trim(),
            processor.getPhysicalProcessorCount(),
            processor.getLogicalProcessorCount(),
            round1(totalLoad),
            perCorePct,
            loadAverage,
            nullIfNonPositive(processor.getMaxFreq()),
            avgPositive(processor.getCurrentFreq()));

    // ── Memory ─────────────────────────────────────────────────────────────
    GlobalMemory mem = hardware.getMemory();
    long total = mem.getTotal();
    long available = mem.getAvailable();
    long used = total - available;
    MemoryMetrics memory =
        new MemoryMetrics(
            total,
            used,
            available,
            total > 0 ? round1(used * 100.0 / total) : 0.0,
            mem.getVirtualMemory().getSwapTotal(),
            mem.getVirtualMemory().getSwapUsed());

    // ── Sensors (frequently unavailable without elevated privileges) ──────
    Sensors sensors = hardware.getSensors();
    double tempRaw = sensors.getCpuTemperature();
    Double cpuTemp = (tempRaw > 0 && !Double.isNaN(tempRaw)) ? round1(tempRaw) : null;
    List<Integer> fans = Arrays.stream(sensors.getFanSpeeds()).filter(f -> f > 0).boxed().toList();
    double voltRaw = sensors.getCpuVoltage();
    Double voltage = voltRaw > 0 ? round2(voltRaw) : null;
    SensorMetrics sensorMetrics =
        new SensorMetrics(cpuTemp, fans.isEmpty() ? List.of() : fans, voltage);

    // ── GPUs ───────────────────────────────────────────────────────────────
    List<GpuMetrics> gpus = collectGpus();

    HostInfo host =
        new HostInfo(
            os.getNetworkParams().getHostName(),
            os.toString(),
            System.getProperty("os.arch"),
            os.getSystemUptime(),
            os.getProcessCount(),
            os.getThreadCount());

    return new HostMetricsResponse(
        Instant.now().toString(), host, cpu, memory, sensorMetrics, gpus);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // GPU collection
  // ──────────────────────────────────────────────────────────────────────────

  private List<GpuMetrics> collectGpus() {
    List<GraphicsCard> cards = hardware.getGraphicsCards();

    // Vendor-tool readings, index-aligned where possible.
    List<NvidiaGpuSample> nvidia = queryNvidiaSmi();
    Double macUtilization = queryMacGpuUtilization();

    List<GpuMetrics> out = new ArrayList<>();
    int nvidiaIdx = 0;
    for (GraphicsCard card : cards) {
      Double utilization = null;
      Long memUsed = null;
      Double temp = null;

      boolean isNvidia = card.getVendor().toLowerCase(Locale.ROOT).contains("nvidia");
      if (isNvidia && nvidiaIdx < nvidia.size()) {
        NvidiaGpuSample s = nvidia.get(nvidiaIdx++);
        utilization = s.utilizationPct;
        memUsed = s.memoryUsedBytes;
        temp = s.tempC;
      } else if (macUtilization != null && out.isEmpty()) {
        // macOS reports a single accelerator utilization — attach to the first card.
        utilization = macUtilization;
      }

      out.add(
          new GpuMetrics(
              card.getName(), card.getVendor(), card.getVRam(), utilization, memUsed, temp));
    }

    // nvidia-smi saw GPUs that OSHI did not enumerate (rare) — still surface them.
    while (nvidiaIdx < nvidia.size()) {
      NvidiaGpuSample s = nvidia.get(nvidiaIdx++);
      out.add(
          new GpuMetrics(
              s.name, "NVIDIA", s.memoryTotalBytes, s.utilizationPct, s.memoryUsedBytes, s.tempC));
    }
    return out;
  }

  private record NvidiaGpuSample(
      String name,
      Double utilizationPct,
      Long memoryUsedBytes,
      long memoryTotalBytes,
      Double tempC) {}

  /** Queries {@code nvidia-smi} (Linux/Windows/WSL). Returns an empty list when unavailable. */
  private List<NvidiaGpuSample> queryNvidiaSmi() {
    if (Boolean.FALSE.equals(nvidiaSmiAvailable)) {
      return List.of();
    }
    List<String> lines =
        exec(
            2,
            "nvidia-smi",
            "--query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu",
            "--format=csv,noheader,nounits");
    if (lines == null) {
      nvidiaSmiAvailable = false;
      return List.of();
    }
    nvidiaSmiAvailable = true;

    List<NvidiaGpuSample> samples = new ArrayList<>();
    for (String line : lines) {
      String[] parts = line.split(",");
      if (parts.length < 5) {
        continue;
      }
      try {
        samples.add(
            new NvidiaGpuSample(
                parts[0].trim(),
                Double.parseDouble(parts[1].trim()),
                (long) (Double.parseDouble(parts[2].trim()) * 1024 * 1024),
                (long) (Double.parseDouble(parts[3].trim()) * 1024 * 1024),
                Double.parseDouble(parts[4].trim())));
      } catch (NumberFormatException e) {
        log.debug("Unparseable nvidia-smi line: {}", line);
      }
    }
    return samples;
  }

  /**
   * macOS only: parses {@code "Device Utilization %"} from the IOAccelerator registry entry. Works
   * without elevated privileges on Apple Silicon and most Intel-era Macs.
   */
  private Double queryMacGpuUtilization() {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
      return null;
    }
    List<String> lines = exec(2, "ioreg", "-r", "-d", "1", "-w", "0", "-c", "IOAccelerator");
    if (lines == null) {
      return null;
    }
    for (String line : lines) {
      Matcher m = IOREG_UTILIZATION.matcher(line);
      if (m.find()) {
        return Double.parseDouble(m.group(1));
      }
    }
    return null;
  }

  /**
   * Runs a command with a hard timeout, returning stdout lines or {@code null} on any failure
   * (missing binary, non-zero exit, timeout).
   */
  private List<String> exec(int timeoutSeconds, String... command) {
    Process process = null;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(false).start();
      List<String> lines = new ArrayList<>();
      try (BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lines.add(line);
        }
      }
      if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS) || process.exitValue() != 0) {
        return null;
      }
      return lines;
    } catch (Exception e) {
      log.debug("Command {} failed: {}", command[0], e.getMessage());
      return null;
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Small numeric helpers
  // ──────────────────────────────────────────────────────────────────────────

  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }

  private static Double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private static Long nullIfNonPositive(long v) {
    return v > 0 ? v : null;
  }

  /** Average of the positive entries, or {@code null} when none are reported. */
  private static Long avgPositive(long[] values) {
    long sum = 0;
    int n = 0;
    for (long v : values) {
      if (v > 0) {
        sum += v;
        n++;
      }
    }
    return n > 0 ? sum / n : null;
  }
}
