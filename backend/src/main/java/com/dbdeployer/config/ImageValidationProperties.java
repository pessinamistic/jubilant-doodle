package com.dbdeployer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for image status checks and background refresh schedules. */
@Data
@ConfigurationProperties(prefix = "dbdeployer.image-validation")
public class ImageValidationProperties {

  /** Timeout in milliseconds for Docker Hub API calls. */
  private int dockerHubTimeoutMs = 4000;

  /** Local image refresh interval in milliseconds. */
  private long localRefreshIntervalMs = 120_000;

  /** Docker Hub refresh interval in milliseconds. */
  private long dockerHubRefreshIntervalMs = 21_600_000;

  /** When true, background refresh jobs are enabled. */
  private boolean schedulerEnabled = true;

  /**
   * Maximum number of concurrent Docker Hub checkTag HTTP calls during a batch
   * refresh. Keeps the app from hammering the public API and hitting 429
   * rate-limit responses.
   */
  private int hubRequestConcurrency = 10;

}
