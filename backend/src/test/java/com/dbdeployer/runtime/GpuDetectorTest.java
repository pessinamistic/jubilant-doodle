package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class GpuDetectorTest {

  @Test
  void nvidia_runtime_present_detects_nvidia_with_container_gpu() {
    var profile =
        GpuDetector.classify(
            Set.of("runc", "nvidia"), false, false, "amd64", "Linux", 32_000, 16, 24_000);

    assertThat(profile.gpuVendor()).isEqualTo(GpuVendor.NVIDIA);
    assertThat(profile.containerGpu()).isTrue();
    assertThat(profile.vramMb()).isEqualTo(24_000);
    assertThat(profile.effectiveVramMb()).isEqualTo(24_000);
  }

  @Test
  void amd_device_files_present_detects_amd() {
    var profile =
        GpuDetector.classify(Set.of("runc"), true, true, "amd64", "Linux", 16_000, 8, 8_000);

    assertThat(profile.gpuVendor()).isEqualTo(GpuVendor.AMD);
    assertThat(profile.containerGpu()).isTrue();
  }

  @Test
  void apple_silicon_detected_but_container_gpu_is_false() {
    var profile =
        GpuDetector.classify(Set.of("runc"), false, false, "aarch64", "Mac OS X", 24_000, 10, 0);

    assertThat(profile.gpuVendor()).isEqualTo(GpuVendor.APPLE);
    assertThat(profile.containerGpu()).isFalse();
    // Unified memory: effective VRAM is total RAM.
    assertThat(profile.effectiveVramMb()).isEqualTo(24_000);
  }

  @Test
  void no_gpu_signals_detects_none() {
    var profile = GpuDetector.classify(Set.of("runc"), false, false, "amd64", "Linux", 8_000, 4, 0);

    assertThat(profile.gpuVendor()).isEqualTo(GpuVendor.NONE);
    assertThat(profile.containerGpu()).isFalse();
    assertThat(profile.effectiveVramMb()).isZero();
  }

  @Test
  void apple_takes_priority_over_nvidia_runtime_signal() {
    // Even if a stray nvidia runtime appears, aarch64 macOS is classified as Apple.
    var profile =
        GpuDetector.classify(
            Set.of("runc", "nvidia"), false, false, "arm64", "Mac OS X", 16_000, 8, 0);

    assertThat(profile.gpuVendor()).isEqualTo(GpuVendor.APPLE);
  }
}
