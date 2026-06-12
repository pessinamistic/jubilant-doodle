package com.dbdeployer.pipeline;

import lombok.Getter;
import lombok.Setter;
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
@Setter
@Getter
@ConfigurationProperties(prefix = "dbdeployer.pipeline")
public class PipelineProperties {

  /** Delay in ms between each pipeline step. Default: 1500. */
  private long stepDelayMs = 1500;
}
