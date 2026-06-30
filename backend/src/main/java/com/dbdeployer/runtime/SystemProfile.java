package com.dbdeployer.runtime;

/**
 * A snapshot of the host's hardware capability, aggregated by {@link GpuDetector}. Drives the Model
 * Cookbook compatibility scoring.
 *
 * @param gpuVendor detected GPU vendor (or {@link GpuVendor#NONE})
 * @param vramMb dedicated GPU VRAM in MB (0 when unknown / NONE)
 * @param totalRamMb total system RAM in MB
 * @param cpuCores available CPU cores
 * @param platform short OS/arch label, e.g. {@code "macOS/aarch64"}
 * @param containerGpu whether a containerised runtime can actually use the GPU (false on Apple
 *     Silicon — Docker Desktop's Linux VM has no Metal passthrough)
 */
public record SystemProfile(
    GpuVendor gpuVendor,
    long vramMb,
    long totalRamMb,
    int cpuCores,
    String platform,
    boolean containerGpu) {

  public boolean isAppleSilicon() {
    return gpuVendor == GpuVendor.APPLE;
  }

  /**
   * The VRAM figure used for scoring. On Apple Silicon the GPU shares system memory (unified
   * memory), so total RAM is the effective VRAM ceiling; otherwise it's the dedicated VRAM.
   */
  public long effectiveVramMb() {
    return isAppleSilicon() ? totalRamMb : vramMb;
  }
}
