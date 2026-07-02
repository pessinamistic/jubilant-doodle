package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.model.ModelRuntimeEntity;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.ModelRuntimeRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelRuntimeServiceTest {

  private static final String DEFAULT_URL = "http://localhost:11434";

  @Mock private ModelRuntimeRepository runtimeRepo;
  @Mock private DeployedContainerRepository containerRepo;
  @Mock private DockerDeployEngine docker;

  private ModelRuntimeService service() {
    return new ModelRuntimeService(runtimeRepo, containerRepo, docker, DEFAULT_URL);
  }

  private static DeployedContainer instance(DbType type, InstanceStatus status, int hostPort) {
    var config = new DeploymentConfig();
    config.setId("cfg-" + type.name());
    config.setName(type.name().toLowerCase());
    config.setDbType(type);
    var c = new DeployedContainer();
    c.setId("inst-" + type.name());
    c.setConfig(config);
    c.setHostPort(hostPort);
    c.setStatus(status);
    return c;
  }

  @Test
  void registers_a_runtime_row_for_an_ollama_deploy() {
    var ollama = instance(DbType.OLLAMA, InstanceStatus.RUNNING, 11500);
    when(runtimeRepo.findByConfigId("cfg-OLLAMA")).thenReturn(Optional.empty());
    when(docker.detectGpuVendor()).thenReturn(GpuVendor.NONE);

    service().registerIfModelRuntime(ollama.getConfig(), ollama);

    var captor = ArgumentCaptor.forClass(ModelRuntimeEntity.class);
    verify(runtimeRepo).save(captor.capture());
    ModelRuntimeEntity row = captor.getValue();
    assertThat(row.getRuntimeType()).isEqualTo(ModelRuntime.OLLAMA);
    assertThat(row.getConfigId()).isEqualTo("cfg-OLLAMA");
    assertThat(row.getBaseUrl()).isEqualTo("http://localhost:11500");
    assertThat(row.getGpuVendor()).isEqualTo(GpuVendor.NONE);
    assertThat(row.getId()).isNotBlank();
  }

  @Test
  void re_registering_the_same_config_updates_the_existing_row() {
    var ollama = instance(DbType.OLLAMA, InstanceStatus.RUNNING, 11600);
    var existing = new ModelRuntimeEntity();
    existing.setId("row-1");
    existing.setConfigId("cfg-OLLAMA");
    when(runtimeRepo.findByConfigId("cfg-OLLAMA")).thenReturn(Optional.of(existing));
    when(docker.detectGpuVendor()).thenReturn(GpuVendor.NVIDIA);

    service().registerIfModelRuntime(ollama.getConfig(), ollama);

    var captor = ArgumentCaptor.forClass(ModelRuntimeEntity.class);
    verify(runtimeRepo).save(captor.capture());
    assertThat(captor.getValue().getId()).isEqualTo("row-1");
    assertThat(captor.getValue().getBaseUrl()).isEqualTo("http://localhost:11600");
  }

  @Test
  void non_runtime_types_are_ignored() {
    var pg = instance(DbType.POSTGRESQL, InstanceStatus.RUNNING, 5432);

    service().registerIfModelRuntime(pg.getConfig(), pg);

    verify(runtimeRepo, never()).save(any());
  }

  @Test
  void resolves_base_url_from_the_first_running_managed_ollama() {
    var ollama = instance(DbType.OLLAMA, InstanceStatus.RUNNING, 11777);
    var redis = instance(DbType.REDIS, InstanceStatus.RUNNING, 6379);
    when(containerRepo.findByStatus(InstanceStatus.RUNNING)).thenReturn(List.of(redis, ollama));

    assertThat(service().resolveOllamaBaseUrl()).isEqualTo("http://localhost:11777");
  }

  @Test
  void falls_back_to_the_configured_default_when_no_managed_ollama_runs() {
    when(containerRepo.findByStatus(InstanceStatus.RUNNING)).thenReturn(List.of());

    assertThat(service().resolveOllamaBaseUrl()).isEqualTo(DEFAULT_URL);
  }

  @Test
  void removeForConfig_deletes_the_registry_row() {
    service().removeForConfig("cfg-OLLAMA");

    verify(runtimeRepo).deleteByConfigId("cfg-OLLAMA");
  }
}
