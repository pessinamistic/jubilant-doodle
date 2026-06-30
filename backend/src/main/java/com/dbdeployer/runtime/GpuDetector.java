package com.dbdeployer.runtime;

import com.dbdeployer.deploy.DockerDeployEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Detects the host's GPU capability <em>without ever shelling out to {@code nvidia-smi}</em>
 * (roadmap hard constraint). Signals used:
 *
 * <ul>
 *   <li><b>NVIDIA</b> — Docker exposes an {@code nvidia} container runtime when the NVIDIA
 *       Container Toolkit is installed ({@link DockerDeployEngine#dockerRuntimeNames()}).
 *   <li><b>AMD ROCm</b> — presence of the {@code /dev/kfd} and {@code /dev/dri} device files.
 *   <li><b>Apple Silicon</b> — {@code os.arch == aarch64} on macOS. Note: native Ollama uses the
 *       Metal GPU, but a <em>containerised</em> Ollama on macOS runs CPU-only (Docker Desktop's
 *       Linux VM has no Metal passthrough) — surfaced via {@link SystemProfile#isContainerGpu()}.
 * </ul>
 *
 * <p>Dedicated VRAM cannot be read reliably without vendor tools, so it is best-effort: an optional
 * override ({@code -Dportwrangler.gpu.vram-mb=NNNN} or {@code PORTWRANGLER_GPU_VRAM_MB}) is
 * honoured, otherwise VRAM is reported as 0 and GPU models score conservatively (CPU_ONLY) rather
 * than over-promising.
 */
@Slf4j
@Component
public class GpuDetector {

  private final DockerDeployEngine docker;

  public GpuDetector(DockerDeployEngine docker) {
    this.docker = docker;
  }

  /** Builds a {@link SystemProfile} from live host + Docker signals. */
  public SystemProfile detect() {
    Set<String> runtimes = docker.dockerRuntimeNames();
    boolean kfd = pathExists("/dev/kfd");
    boolean dri = pathExists("/dev/dri");
    String arch = System.getProperty("os.arch", "");
    String osName = System.getProperty("os.name", "");
    long totalRamMb = totalRamMb();
    int cores = Runtime.getRuntime().availableProcessors();
    long vramOverride = vramOverrideMb();
    return classify(runtimes, kfd, dri, arch, osName, totalRamMb, cores, vramOverride);
  }

  /**
   * Pure classification — fully unit-testable against captured signal fixtures (no Docker, no
   * filesystem, no real GPU required).
   */
  public static SystemProfile classify(
      Set<String> dockerRuntimes,
      boolean kfdExists,
      boolean driExists,
      String osArch,
      String osName,
      long totalRamMb,
      int cpuCores,
      long detectedVramMb) {

    String platform = osName + "/" + osArch;
    boolean mac = osName.toLowerCase().contains("mac");
    boolean aarch64 = osArch.equalsIgnoreCase("aarch64") || osArch.equalsIgnoreCase("arm64");

    // Apple Silicon: GPU available to NATIVE Ollama, but NOT to a containerised runtime.
    if (mac && aarch64) {
      return new SystemProfile(GpuVendor.APPLE, 0, totalRamMb, cpuCores, platform, false);
    }
    // NVIDIA Container Toolkit present.
    if (dockerRuntimes.stream().anyMatch(r -> r.toLowerCase().contains("nvidia"))) {
      return new SystemProfile(
          GpuVendor.NVIDIA, detectedVramMb, totalRamMb, cpuCores, platform, true);
    }
    // AMD ROCm device files present.
    if (kfdExists && driExists) {
      return new SystemProfile(GpuVendor.AMD, detectedVramMb, totalRamMb, cpuCores, platform, true);
    }
    return new SystemProfile(GpuVendor.NONE, 0, totalRamMb, cpuCores, platform, false);
  }

  private static boolean pathExists(String p) {
    try {
      return Files.exists(Path.of(p));
    } catch (Exception e) {
      return false;
    }
  }

  private static long totalRamMb() {
    long bytes = osPhysicalMemoryBytes();
    return bytes > 0 ? bytes / (1024 * 1024) : 0;
  }

  /** Reads total physical memory via the JDK's OS MXBean without an extra dependency. */
  private static long osPhysicalMemoryBytes() {
    try {
      var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
      var m = os.getClass().getMethod("getTotalMemorySize");
      m.setAccessible(true);
      return (long) m.invoke(os);
    } catch (Exception ignored) {
      // Older JDKs expose the deprecated name.
      try {
        var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        var m = os.getClass().getMethod("getTotalPhysicalMemorySize");
        m.setAccessible(true);
        return (long) m.invoke(os);
      } catch (Exception e) {
        return 0;
      }
    }
  }

  private static long vramOverrideMb() {
    String prop = System.getProperty("portwrangler.gpu.vram-mb");
    if (prop == null || prop.isBlank()) {
      prop = System.getenv("PORTWRANGLER_GPU_VRAM_MB");
    }
    try {
      return prop != null && !prop.isBlank() ? Long.parseLong(prop.trim()) : 0;
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
