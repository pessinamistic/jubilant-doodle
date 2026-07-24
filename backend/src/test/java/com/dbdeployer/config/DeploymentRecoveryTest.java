package com.dbdeployer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.model.PipelineStatus;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.store.DeployedContainerRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Startup-recovery guard (task P2-A): non-Docker rows never enter the async Docker pipeline, so a
 * stuck DEPLOYING row with a synthetic ("brew:&lt;service&gt;") id must be skipped rather than
 * resolved against Docker, which would misresolve the id.
 */
@ExtendWith(MockitoExtension.class)
class DeploymentRecoveryTest {

  @Mock private DockerDeployEngine dockerDeployEngine;
  @Mock private PipelineStepRepository pipelineStepRepository;
  @Mock private DeployedContainerRepository deployedContainerRepository;
  @Mock private DeploymentPipelineRepository deploymentPipelineRepository;

  private DeploymentRecovery recovery() {
    return new DeploymentRecovery(
        dockerDeployEngine,
        pipelineStepRepository,
        deployedContainerRepository,
        deploymentPipelineRepository);
  }

  private static DeployedContainer deploying(String id, DeployMethod method, String containerId) {
    var config = new DeploymentConfig();
    config.setId("cfg-" + id);
    config.setName(id);
    config.setDeployMethod(method);

    var c = new DeployedContainer();
    c.setId(id);
    c.setConfig(config);
    c.setContainerId(containerId);
    c.setStatus(InstanceStatus.DEPLOYING);
    return c;
  }

  @Test
  void recovery_skips_non_docker_rows() {
    var brewRow = deploying("pg", DeployMethod.HOMEBREW, "brew:postgresql@16");
    when(deployedContainerRepository.findByStatus(InstanceStatus.DEPLOYING))
        .thenReturn(List.of(brewRow));
    when(deploymentPipelineRepository.findByStatus(PipelineStatus.RUNNING)).thenReturn(List.of());

    recovery().run(null);

    verifyNoInteractions(dockerDeployEngine);
    verify(deployedContainerRepository, never()).save(any());
    assertThat(brewRow.getStatus()).isEqualTo(InstanceStatus.DEPLOYING); // left untouched
  }

  @Test
  void recovery_still_resolves_docker_rows() {
    var dockerRow = deploying("db", DeployMethod.DOCKER, null); // no id → marked ERROR
    when(deployedContainerRepository.findByStatus(InstanceStatus.DEPLOYING))
        .thenReturn(List.of(dockerRow));
    when(deploymentPipelineRepository.findByStatus(PipelineStatus.RUNNING)).thenReturn(List.of());

    recovery().run(null);

    assertThat(dockerRow.getStatus()).isEqualTo(InstanceStatus.ERROR);
    verify(deployedContainerRepository).save(dockerRow);
  }
}
