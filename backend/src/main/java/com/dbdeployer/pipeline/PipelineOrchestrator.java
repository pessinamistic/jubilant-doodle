package com.dbdeployer.pipeline;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.*;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orchestrates pipeline creation and fires the async runner.
 *
 * <p>Called inside the same transaction as the deploy service method. Registers an {@code
 * afterCommit} hook so that {@link PipelineRunner#run(String)} is only fired after the pipeline +
 * step rows are committed to the DB, avoiding a not-found race condition in the async thread.
 */
@Service
public class PipelineOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

  private static final List<StepType> STEP_ORDER =
      List.of(
          StepType.PULL_IMAGE,
          StepType.CREATE_CONTAINER,
          StepType.START_CONTAINER,
          StepType.FINALISE);

  private final DeploymentPipelineRepository pipelineRepo;
  private final PipelineStepRepository stepRepo;
  private final PipelineRunner runner;

  public PipelineOrchestrator(
      DeploymentPipelineRepository pipelineRepo,
      PipelineStepRepository stepRepo,
      PipelineRunner runner) {
    this.pipelineRepo = pipelineRepo;
    this.stepRepo = stepRepo;
    this.runner = runner;
  }

  /**
   * Create pipeline + step rows in the current TX, then fire the async runner via {@code
   * afterCommit}.
   *
   * <p>Updates {@code container.latestPipelineId} (caller must persist the container).
   */
  @Transactional
  public DeploymentPipeline createAndLaunch(DeploymentConfig config, DeployedContainer container) {
    // ── Create pipeline row ──
    DeploymentPipeline pipeline = new DeploymentPipeline();
    pipeline.setId(UUID.randomUUID().toString());
    pipeline.setConfigId(config.getId());
    pipeline.setStatus(PipelineStatus.PENDING);
    pipelineRepo.save(pipeline);

    // ── Create step rows ──
    for (int i = 0; i < STEP_ORDER.size(); i++) {
      PipelineStep step = new PipelineStep();
      step.setId(UUID.randomUUID().toString());
      step.setPipeline(pipeline);
      step.setStepType(STEP_ORDER.get(i));
      step.setStepOrder(i);
      step.setStatus(StepStatus.PENDING);
      stepRepo.save(step);
    }

    // ── Update container with pipeline ID ──
    container.setLatestPipelineId(pipeline.getId());

    // ── Fire runner after commit ──
    String pipelineId = pipeline.getId();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            log.info("[orchestrator] TX committed — firing pipeline runner for {}", pipelineId);
            runner.run(pipelineId);
          }
        });

    log.info("[orchestrator] Pipeline {} created for config '{}'", pipelineId, config.getName());
    return pipeline;
  }
}
