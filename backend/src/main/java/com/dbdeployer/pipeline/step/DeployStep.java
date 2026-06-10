package com.dbdeployer.pipeline.step;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.StepType;

/**
 * A single step in the deploy pipeline.
 *
 * <p>
 * Implementations are Spring {@code @Component}s so they can be injected into
 * {@link com.dbdeployer.pipeline.PipelineRunner} via a
 * {@code Map<StepType, DeployStep>}.
 */
public interface DeployStep {

  StepType type();

  /**
   * Execute the step.
   *
   * @param config
   *          the deployment config (read-only — no save inside a step)
   * @param container
   *          the container record; steps MAY mutate fields on it (e.g. set
   *          containerId); the runner persists after each step
   * @return a short human-readable success message
   * @throws StepExecutionException
   *           on a known Docker failure
   */
  String execute(DeploymentConfig config, DeployedContainer container) throws StepExecutionException;
}
