package com.dbdeployer.api;

import com.dbdeployer.api.dto.HostMetricsResponse;
import com.dbdeployer.service.HostMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live hardware metrics of the host machine for the System Health page.
 *
 * <p>{@code GET /api/system/host-metrics} — one snapshot of CPU (total + per-core), memory, swap,
 * sensors (temperature / fans / voltage) and GPU utilization. The frontend polls this every couple
 * of seconds and builds its own rolling time-series client-side, so no history is kept here.
 */
@RestController
@RequestMapping("/system/host-metrics")
public class HostMetricsController {

  private final HostMetricsService hostMetricsService;

  public HostMetricsController(HostMetricsService hostMetricsService) {
    this.hostMetricsService = hostMetricsService;
  }

  @GetMapping
  public HostMetricsResponse metrics() {
    return hostMetricsService.snapshot();
  }
}
