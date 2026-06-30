package com.dbdeployer.os;

import com.dbdeployer.deploy.OsDetector;
import org.springframework.stereotype.Component;

/**
 * Spring component that provides operating system information by delegating to an {@link
 * OsDetector}.
 *
 * <p>This service is a thin wrapper around {@link OsDetector} exposing only the {@link
 * #getSystemInfo()} method to callers. It exists to enable injection of the {@code OsDetector}
 * implementation and to centralize OS-related operations.
 */
@Component
public class OperatingSystemService {

  /** Detector used to obtain system information. */
  private final OsDetector osDetector;

  /**
   * Create a new OperatingSystemService.
   *
   * @param osDetector the {@link OsDetector} used to obtain system information; must not be {@code
   *     null}
   */
  public OperatingSystemService(OsDetector osDetector) {
    this.osDetector = osDetector;
  }

  /**
   * Retrieve system information detected by the {@link OsDetector}.
   *
   * @return an {@link OsDetector.SystemInfo} instance containing detected OS details
   */
  public OsDetector.SystemInfo getSystemInfo() {
    return osDetector.getSystemInfo();
  }
}
