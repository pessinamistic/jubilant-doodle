package com.dbdeployer.os;

import com.dbdeployer.deploy.OsDetector;
import org.springframework.stereotype.Component;

@Component
public class OperatingSystemService {

  private final OsDetector osDetector;

  public OperatingSystemService(OsDetector osDetector) {
    this.osDetector = osDetector;
  }

  public OsDetector.SystemInfo getSystemInfo() {
    return osDetector.getSystemInfo();
  }
}
