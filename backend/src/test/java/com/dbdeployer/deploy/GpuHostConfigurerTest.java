package com.dbdeployer.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbdeployer.runtime.GpuVendor;
import com.github.dockerjava.api.model.HostConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class GpuHostConfigurerTest {

  private final GpuHostConfigurer configurer = new GpuHostConfigurer();

  @Test
  void nvidia_adds_a_gpu_device_request() {
    HostConfig hc = HostConfig.newHostConfig();
    configurer.applyGpu(hc, GpuVendor.NVIDIA);

    assertThat(hc.getDeviceRequests()).hasSize(1);
    var dr = hc.getDeviceRequests().get(0);
    assertThat(dr.getDriver()).isEqualTo("nvidia");
    assertThat(dr.getCount()).isEqualTo(-1);
    assertThat(dr.getCapabilities()).containsExactly(List.of("gpu"));
  }

  @Test
  void amd_adds_kfd_and_dri_devices() {
    HostConfig hc = HostConfig.newHostConfig();
    configurer.applyGpu(hc, GpuVendor.AMD);

    assertThat(hc.getDevices()).hasSize(2);
    assertThat(hc.getDevices())
        .extracting(d -> d.getPathOnHost())
        .containsExactlyInAnyOrder("/dev/kfd", "/dev/dri");
  }

  @Test
  void apple_silicon_adds_no_gpu_access() {
    HostConfig hc = HostConfig.newHostConfig();
    configurer.applyGpu(hc, GpuVendor.APPLE);

    assertThat(hc.getDeviceRequests()).isNullOrEmpty();
    assertThat(hc.getDevices()).isNullOrEmpty();
  }

  @Test
  void none_adds_no_gpu_access() {
    HostConfig hc = HostConfig.newHostConfig();
    configurer.applyGpu(hc, GpuVendor.NONE);

    assertThat(hc.getDeviceRequests()).isNullOrEmpty();
    assertThat(hc.getDevices()).isNullOrEmpty();
  }
}
