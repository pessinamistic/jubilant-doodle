package com.dbdeployer.deploy;

import com.dbdeployer.runtime.GpuVendor;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.DeviceRequest;
import com.github.dockerjava.api.model.HostConfig;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Applies GPU access to a Docker {@link HostConfig} based on the detected {@link GpuVendor}. Pure
 * and side-effect-free apart from mutating the passed {@link HostConfig}, so it is fully
 * unit-testable without a real GPU (roadmap §6 Phase 2).
 *
 * <ul>
 *   <li><b>NVIDIA</b> — a {@code DeviceRequest} with the {@code nvidia} driver, all GPUs ({@code
 *       count = -1}) and the {@code gpu} capability.
 *   <li><b>AMD ROCm</b> — the {@code /dev/kfd} and {@code /dev/dri} device files.
 *   <li><b>Apple / None</b> — nothing: a containerised runtime has no GPU passthrough on macOS, and
 *       there is no GPU to expose otherwise.
 * </ul>
 */
@Slf4j
@Component
public class GpuHostConfigurer {

  /** Mutates and returns {@code hostConfig} with any GPU access the vendor supports. */
  public HostConfig applyGpu(HostConfig hostConfig, GpuVendor vendor) {
    switch (vendor) {
      case NVIDIA -> {
        hostConfig.withDeviceRequests(
            List.of(
                new DeviceRequest()
                    .withDriver("nvidia")
                    .withCount(-1)
                    .withCapabilities(List.of(List.of("gpu")))));
        log.info("[gpu] Applied NVIDIA GPU device request to container host config");
      }
      case AMD -> {
        hostConfig.withDevices(
            new Device("rwm", "/dev/kfd", "/dev/kfd"), new Device("rwm", "/dev/dri", "/dev/dri"));
        log.info("[gpu] Applied AMD ROCm device files (/dev/kfd, /dev/dri) to host config");
      }
      case APPLE, NONE -> {
        // No containerised GPU access available.
      }
    }
    return hostConfig;
  }
}
