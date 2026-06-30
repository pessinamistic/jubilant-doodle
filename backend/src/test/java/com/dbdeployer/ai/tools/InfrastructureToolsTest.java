package com.dbdeployer.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.DeploymentResponse;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.runtime.OllamaModelPuller;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfrastructureToolsTest {

  private static final String OLLAMA_URL = "http://localhost:11434";

  @Mock private com.dbdeployer.service.DbInstanceService service;
  @Mock private ConnectionStringBuilder connBuilder;
  @Mock private com.dbdeployer.service.ConfigTemplateService configTemplate;
  @Mock private DockerDeployEngine docker;
  @Mock private OllamaModelPuller modelPuller;

  private InfrastructureTools tools() {
    return new InfrastructureTools(
        service, connBuilder, configTemplate, docker, modelPuller, OLLAMA_URL);
  }

  private static DeployedContainer container(String name, InstanceStatus status) {
    return container(name, status, DbType.POSTGRESQL, null);
  }

  private static DeployedContainer container(
      String name, InstanceStatus status, DbType type, String containerId) {
    var config = new DeploymentConfig();
    config.setId("cfg-" + name);
    config.setName(name);
    config.setDbType(type);
    config.setVersion("16");
    config.setHostPort(5544);
    var c = new DeployedContainer();
    c.setId("inst-" + name);
    c.setConfig(config);
    c.setContainerId(containerId);
    c.setHostPort(5544);
    c.setStatus(status);
    return c;
  }

  // ── Read-only ────────────────────────────────────────────────────────────────

  @Test
  void listInstances_excludes_removed_and_maps_summaries() {
    var live = container("db", InstanceStatus.RUNNING);
    var gone = container("old", InstanceStatus.REMOVED);
    when(service.listAll()).thenReturn(List.of(live, gone));
    when(connBuilder.buildMasked(live.getConfig()))
        .thenReturn("postgresql://u:****@localhost:5544/d");

    List<InstanceSummary> summaries = tools().listInstances();

    assertThat(summaries).hasSize(1);
    assertThat(summaries.get(0).name()).isEqualTo("db");
    assertThat(summaries.get(0).type()).isEqualTo("POSTGRESQL");
    assertThat(summaries.get(0).hostPort()).isEqualTo(5544);
  }

  @Test
  void stackSummary_counts_active_instances() {
    var live = container("db", InstanceStatus.RUNNING);
    when(service.listAll()).thenReturn(List.of(live));
    when(connBuilder.buildMasked(live.getConfig())).thenReturn("masked");

    StackSummary stack = tools().stackSummary();

    assertThat(stack.count()).isEqualTo(1);
    assertThat(stack.instances()).hasSize(1);
  }

  @Test
  void readLogs_resolves_by_name_and_delegates() throws Exception {
    var live = container("db", InstanceStatus.RUNNING);
    when(service.listAll()).thenReturn(List.of(live));
    when(service.getLogs("inst-db", 50)).thenReturn("log line");

    assertThat(tools().readLogs("db", 50)).isEqualTo("log line");
  }

  @Test
  void unknown_instance_name_throws() {
    when(service.listAll()).thenReturn(List.of());
    assertThatThrownBy(() -> tools().readLogs("nope", 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nope");
  }

  @Test
  void removeInstance_resolves_and_delegates_to_service() {
    var live = container("db", InstanceStatus.RUNNING);
    when(service.listAll()).thenReturn(List.of(live));

    String msg = tools().removeInstance("db");

    verify(service).removeInstance("inst-db");
    assertThat(msg).contains("Removed instance 'db'");
  }

  // ── deployDatabase ─────────────────────────────────────────────────────────-

  @Test
  void deployDatabase_creates_config_then_dispatches_validated_deploy() {
    var config = new DeploymentConfig();
    config.setId("cfg-new");
    var resultContainer = container("mydb", InstanceStatus.DEPLOYING);
    resultContainer.setLatestPipelineId("pipe-1");
    when(configTemplate.create(any())).thenReturn(config);
    when(service.deploy(any(), eq(config), eq(false)))
        .thenReturn(new DeploymentResponse(config, resultContainer));

    String msg = tools().deployDatabase("POSTGRESQL", "mydb", "16", 5544);

    var reqCaptor = ArgumentCaptor.forClass(com.dbdeployer.api.dto.ConfigTemplateRequest.class);
    verify(configTemplate).create(reqCaptor.capture());
    verify(service).deploy(any(), eq(config), eq(false));
    var req = reqCaptor.getValue();
    assertThat(req.name()).isEqualTo("mydb");
    assertThat(req.dbType()).isEqualTo(DbType.POSTGRESQL);
    assertThat(req.version()).isEqualTo("16");
    assertThat(req.hostPort()).isEqualTo(5544);
    assertThat(msg).contains("mydb").contains("5544");
  }

  @Test
  void deployDatabase_defaults_version_and_port_from_catalog() {
    var config = new DeploymentConfig();
    when(configTemplate.create(any())).thenReturn(config);
    when(service.deploy(any(), any(), eq(false)))
        .thenReturn(new DeploymentResponse(config, container("redis", InstanceStatus.DEPLOYING)));

    tools().deployDatabase("redis", "cache", "  ", 0);

    var reqCaptor = ArgumentCaptor.forClass(com.dbdeployer.api.dto.ConfigTemplateRequest.class);
    verify(configTemplate).create(reqCaptor.capture());
    var req = reqCaptor.getValue();
    assertThat(req.dbType()).isEqualTo(DbType.REDIS);
    assertThat(req.version()).isNotBlank(); // catalog-recommended default, not blank
    assertThat(req.hostPort()).isGreaterThan(0); // catalog default port
  }

  @Test
  void deployDatabase_rejects_unknown_type() {
    assertThatThrownBy(() -> tools().deployDatabase("NOT_A_DB", "x", "1", 1234))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown service type");
  }

  // ── createKafkaTopic ─────────────────────────────────────────────────────────

  @Test
  void createKafkaTopic_execs_topic_command_against_running_container() {
    var kafka = container("events", InstanceStatus.RUNNING, DbType.KAFKA, "docker-abc");
    when(service.listAll()).thenReturn(List.of(kafka));
    when(docker.execCapture(eq("docker-abc"), any(String[].class), anyInt()))
        .thenReturn("Created topic orders.");

    String msg = tools().createKafkaTopic("events", "orders", 3);

    var cmdCaptor = ArgumentCaptor.forClass(String[].class);
    verify(docker).execCapture(eq("docker-abc"), cmdCaptor.capture(), anyInt());
    List<String> cmd = List.of(cmdCaptor.getValue());
    assertThat(cmd).contains("--create", "--topic", "orders", "--partitions", "3");
    assertThat(msg).contains("orders").contains("events");
  }

  @Test
  void createKafkaTopic_rejects_non_kafka_instance() {
    var pg = container("db", InstanceStatus.RUNNING, DbType.POSTGRESQL, "docker-pg");
    when(service.listAll()).thenReturn(List.of(pg));

    assertThatThrownBy(() -> tools().createKafkaTopic("db", "t", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a Kafka instance");
  }

  @Test
  void createKafkaTopic_throws_when_exec_fails() {
    var kafka = container("events", InstanceStatus.RUNNING, DbType.KAFKA, "docker-abc");
    when(service.listAll()).thenReturn(List.of(kafka));
    when(docker.execCapture(eq("docker-abc"), any(String[].class), anyInt())).thenReturn(null);

    assertThatThrownBy(() -> tools().createKafkaTopic("events", "orders", 0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to create topic");
  }

  // ── pullModel ────────────────────────────────────────────────────────────────

  @Test
  void pullModel_delegates_to_puller_with_configured_base_url() {
    when(modelPuller.pull(OLLAMA_URL, "llama3.1:8b"))
        .thenReturn(new OllamaModelPuller.PullResult(true, "Pulled model: llama3.1:8b"));

    String msg = tools().pullModel("llama3.1:8b");

    assertThat(msg).isEqualTo("Pulled model: llama3.1:8b");
  }

  @Test
  void pullModel_throws_on_failure() {
    when(modelPuller.pull(OLLAMA_URL, "bad:model"))
        .thenReturn(new OllamaModelPuller.PullResult(false, "Pull failed for bad:model"));

    assertThatThrownBy(() -> tools().pullModel("bad:model"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("bad:model");
  }
}
