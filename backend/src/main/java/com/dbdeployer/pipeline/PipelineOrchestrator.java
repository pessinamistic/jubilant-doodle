package com.dbdeployer.pipeline;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;
import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import com.dbdeployer.pipeline.step.DeployStep;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Orchestrates pipeline creation and fires the async runner.
 *
 * <p>
 * Called inside the same transaction as the deployment service method.
 * Registers an {@code
 * afterCommit} hook so that {@link PipelineRunner#run(String)} is only fired
 * after the pipeline + step rows are committed to the DB, avoiding a not-found
 * race condition in the async thread.
 */
@Slf4j
@Service
public class PipelineOrchestrator {

  private final PipelineRunner pipelineRunner;
  private final PipelineStepRepository stepRepo;
  private final List<DeployStep> pipelineHandlers;
  private final DeploymentPipelineRepository pipelineRepo;

  public PipelineOrchestrator(
    PipelineRunner pipelineRunner,
    PipelineStepRepository stepRepo,
    DeploymentPipelineRepository pipelineRepo,
    @Qualifier("pipelineHandlers") List<DeployStep> pipelineHandlers) {
    this.pipelineRunner = pipelineRunner;
    this.stepRepo = stepRepo;
    this.pipelineRepo = pipelineRepo;
    this.pipelineHandlers = pipelineHandlers;
  }

  /**
   * Create pipeline + step rows in the current TX, then fire the async runner via
   * {@code
   * afterCommit}.
   *
   * <p>
   * Updates {@code container.latestPipelineId} (caller must persist the
   * container).
   */
  @Transactional
  public DeploymentPipeline createAndLaunch(
    DeploymentConfig config,
    DeployedContainer container) {
    // ── Create pipeline row ──
    DeploymentPipeline pipeline = new DeploymentPipeline();
    pipeline.setId(UUID.randomUUID().toString());
    pipeline.setDeploymentContainerId(container.getId());
    pipeline.setConfigId(config.getId());
    pipeline.setStatus(PipelineStatus.PENDING);
    pipelineRepo.save(pipeline);

    // ── Create step rows ──
    AtomicInteger counter = new AtomicInteger(0);
    for (DeployStep deployStep : pipelineHandlers) {
      PipelineStep step = new PipelineStep();
      step.setId(UUID.randomUUID().toString());
      step.setPipeline(pipeline);
      step.setStepType(deployStep.type());
      step.setStepOrder(counter.getAndIncrement());
      step.setStatus(StepStatus.PENDING);
      stepRepo.save(step);
    }

    // ── Update container with pipeline ID ──
    container.setLatestPipelineId(pipeline.getId());

    // ── Fire runner after commit ──
    String pipelineId = pipeline.getId();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        log.info("[orchestrator] TX committed — firing pipeline runner for {}", pipelineId);
        pipelineRunner.run(pipelineId);
      }
    });

    log.info("[orchestrator] Pipeline {} created for config '{}'", pipelineId, config.getName());
    return pipeline;
  }
}
