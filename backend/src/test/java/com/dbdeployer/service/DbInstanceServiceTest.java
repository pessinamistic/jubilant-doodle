package com.dbdeployer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbdeployer.api.dto.ContainerMetricsResponse;
import com.dbdeployer.deploy.BrewDeployEngine;
import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.deploy.ToolMetricsProbe;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.PipelineOrchestrator;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.runtime.ModelRuntimeService;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import com.dbdeployer.validations.DeploymentValidations;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Engine-separation guard tests (task P2-A): every Docker-only path must run only for {@link
 * DeployMethod#DOCKER} (null tolerated as legacy Docker), Homebrew must route to {@link
 * BrewDeployEngine}, and any other method must fail loudly instead of silently hitting Docker with
 * a non-Docker container id.
 */
@ExtendWith(MockitoExtension.class)
class DbInstanceServiceTest {

  @Mock private BrewDeployEngine brew;
  @Mock private DockerDeployEngine docker;
  @Mock private ToolMetricsProbe toolMetrics;
  @Mock private PipelineStepRepository stepRepo;
  @Mock private PipelineOrchestrator orchestrator;
  @Mock private DeploymentConfigRepository configRepo;
  @Mock private DeployedContainerRepository containerRepo;
  @Mock private DeploymentPipelineRepository pipelineRepo;
  @Mock private DeploymentValidations deploymentValidations;
  @Mock private ModelRuntimeService modelRuntimeService;
  @Mock private ApplicationEventPublisher events;

  private DbInstanceService service() {
    return new DbInstanceService(
        brew,
        docker,
        toolMetrics,
        stepRepo,
        orchestrator,
        configRepo,
        containerRepo,
        pipelineRepo,
        deploymentValidations,
        modelRuntimeService,
        events);
  }

  private static DeployedContainer container(
      String id, DeployMethod method, String containerId, String containerName) {
    var config = new DeploymentConfig();
    config.setId("cfg-" + id);
    config.setName(id);
    config.setDbType(DbType.POSTGRESQL);
    config.setVersion("16");
    config.setHostPort(5544);
    config.setDeployMethod(method);

    var c = new DeployedContainer();
    c.setId(id);
    c.setConfig(config);
    c.setContainerId(containerId);
    c.setContainerName(containerName);
    c.setHostPort(5544);
    c.setStatus(InstanceStatus.STOPPED);
    return c;
  }

  // ── Metrics ──────────────────────────────────────────────────────────────────

  @Test
  void metrics_returns_unavailable_for_homebrew_without_touching_docker() {
    var brewInstance =
        container("pg", DeployMethod.HOMEBREW, "brew:postgresql@16", "postgresql@16");
    when(containerRepo.findById("pg")).thenReturn(Optional.of(brewInstance));

    ContainerMetricsResponse metrics = service().getContainerMetrics("pg");

    assertThat(metrics.available()).isFalse();
    verify(docker, never()).getContainerMetrics(anyString(), anyInt());
    verifyNoInteractions(toolMetrics);
  }

  @Test
  void metrics_for_docker_instance_probes_the_docker_engine() {
    var dockerInstance = container("db", DeployMethod.DOCKER, "docker-abc", "dbdeployer-db");
    when(containerRepo.findById("db")).thenReturn(Optional.of(dockerInstance));
    when(docker.getContainerMetrics("docker-abc", 5544))
        .thenReturn(ContainerMetricsResponse.unavailable());

    ContainerMetricsResponse metrics = service().getContainerMetrics("db");

    assertThat(metrics.available()).isFalse();
    verify(docker).getContainerMetrics("docker-abc", 5544);
  }

  // ── Rename ───────────────────────────────────────────────────────────────────

  @Test
  void rename_on_homebrew_is_config_only_and_skips_docker_rename() {
    var brewInstance =
        container("pg", DeployMethod.HOMEBREW, "brew:postgresql@16", "postgresql@16");
    when(containerRepo.findById("pg")).thenReturn(Optional.of(brewInstance));

    service().rename("pg", "my-postgres");

    // The Homebrew service name is derived from the synthetic containerId, so the Docker rename
    // must
    // never fire; only our tracked record is updated.
    verify(docker, never()).renameContainer(any(), anyString());
    verify(containerRepo).save(brewInstance);
    assertThat(brewInstance.getContainerName()).isEqualTo("my-postgres");
  }

  @Test
  void rename_on_docker_renames_the_container() {
    var dockerInstance = container("db", DeployMethod.DOCKER, "docker-abc", "dbdeployer-db");
    when(containerRepo.findById("db")).thenReturn(Optional.of(dockerInstance));

    service().rename("db", "renamed-db");

    verify(docker).renameContainer(dockerInstance, "renamed-db");
    verify(containerRepo).save(dockerInstance);
  }

  @Test
  void null_deploy_method_is_treated_as_legacy_docker() {
    var legacy = container("legacy", null, "docker-legacy", "dbdeployer-legacy");
    when(containerRepo.findById("legacy")).thenReturn(Optional.of(legacy));

    service().rename("legacy", "legacy-renamed");

    verify(docker).renameContainer(legacy, "legacy-renamed");
  }

  // ── Polarity flip: unsupported methods are rejected, never routed to Docker ────

  @Test
  void docker_only_op_rejects_methods_with_no_engine() {
    // Defensive contract, asserted in code rather than a comment: no production path creates
    // APT/CHOCOLATEY/WINGET/EMBEDDED rows today (detectImportMethod only ever yields DOCKER or
    // HOMEBREW). If one ever appears, a Docker-only lifecycle op must fail loudly rather than send
    // a
    // non-Docker container id to the Docker engine.
    var svc = service();
    for (DeployMethod method :
        List.of(
            DeployMethod.APT,
            DeployMethod.CHOCOLATEY,
            DeployMethod.WINGET,
            DeployMethod.EMBEDDED)) {
      var c = container("svc-" + method, method, "unmanaged-id", "unmanaged-id");
      when(containerRepo.findById(c.getId())).thenReturn(Optional.of(c));

      assertThatThrownBy(() -> svc.startInstance(c.getId()))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("is not supported by any engine");
    }

    verify(docker, never()).start(any());
    verifyNoInteractions(brew);
    verify(containerRepo, never()).save(any());
  }
}
