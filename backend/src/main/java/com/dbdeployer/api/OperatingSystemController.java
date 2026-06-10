package com.dbdeployer.api;

import com.dbdeployer.os.OperatingSystemService;
import com.dbdeployer.service.DbInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/system")
public class OperatingSystemController {

  private final OperatingSystemService operatingSystemService;

  public OperatingSystemController(
    OperatingSystemService operatingSystemService) {
    this.operatingSystemService = operatingSystemService;
  }

  /** Get system info (OS, available tools) */
  @GetMapping()
  public Object systemInfo() {
    return operatingSystemService.getSystemInfo();
  }

}
