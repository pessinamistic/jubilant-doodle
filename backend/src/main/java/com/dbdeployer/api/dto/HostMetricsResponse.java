package com.dbdeployer.api.dto;

import java.util.List;

/**
 * Live hardware metrics of the machine the backend is running on. Returned by {@code GET
 * /api/system/host-metrics} and rendered by the System Health page.
 *
 * <p>All values are best-effort and cross-platform (macOS / Windows / Linux via OSHI). Fields that
 * cannot be read on the current platform (e.g. CPU temperature without elevated privileges, GPU
 * utilization on unsupported vendors) are {@code null} so the UI can show "N/A" instead of a bogus
 * zero.
 */
public record HostMetricsResponse(
    String timestamp, // ISO-8601
    HostInfo host,
    CpuMetrics cpu,
    MemoryMetrics memory,
    SensorMetrics sensors,
    List<GpuMetrics> gpus) {

  /** Static-ish host identity + process/thread counts. */
  public record HostInfo(
      String hostname,
      String os,
      String arch,
      long uptimeSeconds,
      int processCount,
      int threadCount) {}

  /** CPU load and topology. Loads are percentages 0–100. */
  public record CpuMetrics(
      String model,
      int physicalCores,
      int logicalCores,
      double totalLoadPct,
      List<Double> perCoreLoadPct,
      List<Double>
          loadAverage, // 1 / 5 / 15 min; entries may be negative when unsupported (Windows)
      Long maxFreqHz,
      Long currentFreqHz) {}

  /** Physical memory + swap. */
  public record MemoryMetrics(
      long totalBytes,
      long usedBytes,
      long availableBytes,
      double usedPct,
      long swapTotalBytes,
      long swapUsedBytes) {}

  /** Thermals — {@code null} when the platform does not expose them without elevated rights. */
  public record SensorMetrics(Double cpuTempC, List<Integer> fanSpeedsRpm, Double cpuVoltage) {}

  /** One graphics card. Utilization/temp come from vendor tools when available. */
  public record GpuMetrics(
      String name,
      String vendor,
      long vramBytes,
      Double utilizationPct,
      Long memoryUsedBytes,
      Double tempC) {}
}
