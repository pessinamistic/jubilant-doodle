package com.dbdeployer.api;

import com.dbdeployer.api.dto.SystemDbStatsResponse;
import com.dbdeployer.os.OperatingSystemService;
import com.dbdeployer.service.SystemDbStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/system")
public class OperatingSystemController {

  private final SystemDbStatsService statsService;
  private final OperatingSystemService operatingSystemService;

  public OperatingSystemController(
      SystemDbStatsService statsService, OperatingSystemService operatingSystemService) {
    this.statsService = statsService;
    this.operatingSystemService = operatingSystemService;
  }

  /** Get system info (OS, available tools) */
  @GetMapping()
  public Object systemInfo() {
    return operatingSystemService.getSystemInfo();
  }

  /** Live stats for the system database (schema row counts, pool, JVM heap, uptime) */
  @GetMapping("/stats")
  public SystemDbStatsResponse systemStats() {
    return statsService.getStats();
  }
}
