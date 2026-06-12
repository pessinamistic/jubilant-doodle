package com.dbdeployer.config;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;
import com.dbdeployer.pipeline.model.StepStatus;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.store.DeployedContainerRepository;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup recovery pass that resolves entities stuck in transitional states.
 *
 * <p>
 * Handles two categories:
 *
 * <ol>
 * <li><b>Containers stuck in DEPLOYING</b> — app crashed before async deploy
 * finished. If {@code
 *       containerId == null}: mark ERROR. Otherwise, ask Docker for ground
 * truth.
 * <li><b>Pipelines stuck in RUNNING</b> — app crashed while a pipeline was
 * executing. Mark the pipeline FAILED, mark RUNNING steps FAILED, mark PENDING
 * steps SKIPPED.
 * </ol>
 *
 * Runs at Order(3) — after SystemDbRegistrar (Order 1) and H2DataMigrator
 * (Order 2).
 */
@Slf4j
@Order(3)
@Component
public class DeploymentRecovery implements ApplicationRunner {

  private final DockerDeployEngine dockerDeployEngine;
  private final PipelineStepRepository pipelineStepRepository;
  private final DeployedContainerRepository deployedContainerRepository;
  private final DeploymentPipelineRepository deploymentPipelineRepository;

  public DeploymentRecovery(
    DockerDeployEngine dockerDeployEngine,
    PipelineStepRepository pipelineStepRepository,
    DeployedContainerRepository deployedContainerRepository,
    DeploymentPipelineRepository deploymentPipelineRepository) {
    this.dockerDeployEngine = dockerDeployEngine;
    this.pipelineStepRepository = pipelineStepRepository;
    this.deployedContainerRepository = deployedContainerRepository;
    this.deploymentPipelineRepository = deploymentPipelineRepository;
  }

  @Override
  @Transactional
  public void run(
    ApplicationArguments args) {
    recoverContainers();
    recoverPipelines();
  }

  // ── Container recovery ─────────────────────────────────────────────────────

  private void recoverContainers() {
    List<DeployedContainer> stuck = deployedContainerRepository.findByStatus(InstanceStatus.DEPLOYING);
    if (stuck.isEmpty())
      return;

    log.info("DeploymentRecovery: {} container(s) stuck in DEPLOYING — resolving...", stuck.size());

    for (DeployedContainer container : stuck) {
      String name = container.getConfig() != null ? container.getConfig().getName() : container.getId();

      if (container.getContainerId() == null) {
        log.warn("DeploymentRecovery: '{}' has no containerId — marking ERROR", name);
        container.setStatus(InstanceStatus.ERROR);
      } else {
        InstanceStatus actual = dockerDeployEngine.getStatus(container);
        log.info("DeploymentRecovery: '{}' has containerId — Docker reports {}", name, actual);
        container.setStatus(actual);
        if (actual == InstanceStatus.RUNNING && container.getStartedAt() == null) {
          container.setStartedAt(dockerDeployEngine.getStartedAt(container.getContainerId()));
        }
      }
      deployedContainerRepository.save(container);
    }

    log.info("DeploymentRecovery: container recovery done");
  }

  // ── Pipeline recovery ──────────────────────────────────────────────────────

  private void recoverPipelines() {
    List<DeploymentPipeline> stuck = deploymentPipelineRepository.findByStatus(PipelineStatus.RUNNING);
    if (stuck.isEmpty())
      return;

    log.info("DeploymentRecovery: {} pipeline(s) stuck in RUNNING — marking FAILED...", stuck.size());

    for (DeploymentPipeline pipeline : stuck) {
      log.warn("DeploymentRecovery: pipeline {} stuck RUNNING — marking FAILED", pipeline.getId());

      // RUNNING steps → FAILED; PENDING steps → SKIPPED
      pipelineStepRepository.findByPipelineIdOrderByStepOrderAsc(pipeline.getId()).forEach(step -> {
        if (step.getStatus() == StepStatus.RUNNING) {
          step.setStatus(StepStatus.FAILED);
          step.setMessage("Recovered: app restarted while step was running");
          step.setCompletedAt(Instant.now());
          pipelineStepRepository.save(step);
        } else if (step.getStatus() == StepStatus.PENDING) {
          step.setStatus(StepStatus.SKIPPED);
          step.setCompletedAt(Instant.now());
          pipelineStepRepository.save(step);
        }
      });

      pipeline.setStatus(PipelineStatus.FAILED);
      pipeline.setErrorMessage("Recovered: app restarted while pipeline was running");
      pipeline.setCompletedAt(Instant.now());
      deploymentPipelineRepository.save(pipeline);
    }

    log.info("DeploymentRecovery: pipeline recovery done");
  }
}
