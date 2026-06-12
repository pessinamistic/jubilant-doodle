package com.dbdeployer.pipeline;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;
import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import com.dbdeployer.pipeline.model.StepType;
import com.dbdeployer.pipeline.step.DeployStep;
import com.dbdeployer.pipeline.step.StepExecutionException;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Executes a deployment pipeline asynchronously, step by step.
 *
 * <p>Re-fetches all entities from the DB by ID before doing any work so that the caller's
 * transaction is guaranteed to have committed first.
 */
@Slf4j
@Service
public class PipelineRunner {

  private final PipelineProperties pipelineProperties;
  private final PipelineStepRepository pipelineStepRepository;
  private final Map<StepType, DeployStep> deploymentStepRegistry;
  private final DeploymentConfigRepository deploymentConfigRepository;
  private final DeployedContainerRepository deployedContainerRepository;
  private final DeploymentPipelineRepository deploymentPipelineRepository;

  public PipelineRunner(
      List<DeployStep> steps,
      PipelineProperties pipelineProperties,
      PipelineStepRepository pipelineStepRepository,
      DeploymentConfigRepository deploymentConfigRepository,
      DeployedContainerRepository deployedContainerRepository,
      DeploymentPipelineRepository deploymentPipelineRepository) {
    this.pipelineProperties = pipelineProperties;
    this.pipelineStepRepository = pipelineStepRepository;
    this.deploymentConfigRepository = deploymentConfigRepository;
    this.deployedContainerRepository = deployedContainerRepository;
    this.deploymentPipelineRepository = deploymentPipelineRepository;
    this.deploymentStepRegistry =
        steps.stream().collect(Collectors.toMap(DeployStep::type, Function.identity()));
  }

  @Async
  public void run(String pipelineId) {
    log.info("[runner] Starting pipeline {}", pipelineId);

    // ── Re-fetch everything fresh (post-TX) ──
    DeploymentPipeline pipeline =
        deploymentPipelineRepository
            .findById(pipelineId)
            .orElseThrow(() -> new IllegalStateException("Pipeline not found: " + pipelineId));

    DeploymentConfig config =
        deploymentConfigRepository
            .findById(pipeline.getConfigId())
            .orElseThrow(
                () -> new IllegalStateException("Config not found for pipeline: " + pipelineId));

    DeployedContainer container =
        deployedContainerRepository
            .findById(pipeline.getDeploymentContainerId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Container record not found for config: " + config.getId()));

    List<PipelineStep> pipelineSteps =
        pipelineStepRepository.findByPipelineIdOrderByStepOrderAsc(pipelineId);

    // ── Mark pipeline RUNNING ──
    pipeline.setStatus(PipelineStatus.RUNNING);
    pipeline.setStartedAt(Instant.now());
    deploymentPipelineRepository.save(pipeline);

    boolean failed = false;

    for (PipelineStep pipelineStep : pipelineSteps) {
      if (failed) {
        pipelineStep.setStatus(StepStatus.SKIPPED);
        pipelineStepRepository.save(pipelineStep);
        continue;
      }

      // ── Step: RUNNING ──
      pipelineStep.setStatus(StepStatus.RUNNING);
      pipelineStep.setStartedAt(Instant.now());
      pipelineStepRepository.save(pipelineStep);

      // ── Inter-pipelineStep delay (cosmetic) ──
      if (pipelineStep.getStepOrder() > 0 && pipelineProperties.getStepDelayMs() > 0) {
        try {
          Thread.sleep(pipelineProperties.getStepDelayMs());
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }

      DeployStep impl = deploymentStepRegistry.get(pipelineStep.getStepType());
      if (impl == null) {
        log.error(
            "[runner] No impl registered for pipelineStep type {}", pipelineStep.getStepType());
        markStepFailed(
            pipelineStep,
            DeployErrorCode.UNEXPECTED_ERROR,
            "No handler registered for pipelineStep: " + pipelineStep.getStepType());
        failed = true;
        continue;
      }

      try {
        String msg = impl.execute(config, container);
        pipelineStep.setStatus(StepStatus.SUCCESS);
        pipelineStep.setMessage(msg);
        pipelineStep.setCompletedAt(Instant.now());
        pipelineStepRepository.save(pipelineStep);
        // Persist any container mutations made by this pipelineStep
        deployedContainerRepository.save(container);
        log.info("[runner] Step {} SUCCESS: {}", pipelineStep.getStepType(), msg);
      } catch (StepExecutionException e) {
        log.error(
            "[runner] Step {} FAILED [{}]: {}",
            pipelineStep.getStepType(),
            e.getErrorCode(),
            e.getMessage(),
            e);
        markStepFailed(pipelineStep, e.getErrorCode(), e.getMessage());
        pipeline.setErrorCode(e.getErrorCode());
        pipeline.setErrorMessage(e.getMessage());
        failed = true;
      } catch (Exception e) {
        log.error(
            "[runner] Step {} unexpected error: {}", pipelineStep.getStepType(), e.getMessage(), e);
        markStepFailed(pipelineStep, DeployErrorCode.UNEXPECTED_ERROR, e.getMessage());
        pipeline.setErrorCode(DeployErrorCode.UNEXPECTED_ERROR);
        pipeline.setErrorMessage(e.getMessage());
        failed = true;
      }
    }

    // ── Finalise pipeline ──
    pipeline.setStatus(failed ? PipelineStatus.FAILED : PipelineStatus.SUCCESS);
    pipeline.setCompletedAt(Instant.now());
    deploymentPipelineRepository.save(pipeline);

    if (failed) {
      // Mark container ERROR if the deployment failed before the container was
      // started
      if (container.getStatus() == InstanceStatus.DEPLOYING) {
        container.setStatus(InstanceStatus.ERROR);
        deployedContainerRepository.save(container);
      }
      log.error(
          "[runner] Pipeline {} FAILED — error: {} — {}",
          pipelineId,
          pipeline.getErrorCode(),
          pipeline.getErrorMessage());
    } else {
      log.info(
          "[runner] Pipeline {} SUCCESS — container {}",
          pipelineId,
          container.getContainerId() != null ? container.getContainerId().substring(0, 12) : "?");
    }
  }

  private void markStepFailed(PipelineStep step, DeployErrorCode code, String message) {
    step.setStatus(StepStatus.FAILED);
    step.setMessage("[" + code + "] " + message);
    step.setCompletedAt(Instant.now());
    pipelineStepRepository.save(step);
  }
}
