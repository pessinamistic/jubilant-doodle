package com.dbdeployer.pipeline;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the deploy pipeline.
 *
 * <pre>
 * dbdeployer:
 *   pipeline:
 *     step-delay-ms: 1500   # pause between steps (ms) — cosmetic, helps UI polling feel smooth
 * </pre>
 */
@ConfigurationProperties(prefix = "dbdeployer.pipeline")
public class PipelineProperties {

  /** Delay in ms between each pipeline step. Default: 1500. */
  private long stepDelayMs = 1500;

  public long getStepDelayMs() {
    return stepDelayMs;
  }

  public void setStepDelayMs(long millis) {
    this.stepDelayMs = millis;
  }
}
