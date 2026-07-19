package com.dbdeployer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComposeExportServiceTest {

  @Mock private DbInstanceService instanceService;
  @Mock private DockerDeployEngine docker;

  private ComposeExportService service() {
    return new ComposeExportService(instanceService, docker);
  }

  private static DeployedContainer container(
      DbType type,
      String name,
      String version,
      int hostPort,
      int containerPort,
      InstanceStatus status) {
    var config = new DeploymentConfig();
    config.setId(UUID.randomUUID().toString());
    config.setName(name);
    config.setDbType(type);
    config.setVersion(version);
    config.setHostPort(hostPort);

    var c = new DeployedContainer();
    c.setId(UUID.randomUUID().toString());
    c.setConfig(config);
    c.setContainerName("dbdeployer-" + name);
    c.setHostPort(hostPort);
    c.setContainerPort(containerPort);
    c.setStatus(status);
    return c;
  }

  @Test
  void emits_valid_compose_block_for_a_redis_instance() {
    var redis = container(DbType.REDIS, "cache", "7.4", 6390, 6379, InstanceStatus.RUNNING);
    when(instanceService.listAll()).thenReturn(List.of(redis));
    when(docker.resolveEnv(redis.getConfig())).thenReturn(List.of("REDIS_PASSWORD=secret"));

    String yaml = service().exportYaml();

    assertThat(yaml)
        .contains("version: \"3.9\"")
        .contains("services:")
        .contains("  cache:")
        .contains("    image: \"redis:7.4\"")
        .contains("    container_name: \"dbdeployer-cache\"")
        .contains("    restart: unless-stopped")
        .contains("      - \"6390:6379\"")
        .contains("      REDIS_PASSWORD: \"secret\"")
        // Redis has a data volume path -> named volume declared
        .contains("      - \"cache-data:/data\"")
        .contains("volumes:")
        .contains("  cache-data:");
  }

  @Test
  void excludes_system_template_and_removed_instances() {
    var system = container(DbType.POSTGRESQL, "system", "16", 5499, 5432, InstanceStatus.RUNNING);
    system.getConfig().setSystem(true);
    var template = container(DbType.POSTGRESQL, "tmpl", "16", 5400, 5432, InstanceStatus.STOPPED);
    template.getConfig().setTemplate(true);
    var removed = container(DbType.REDIS, "gone", "7.4", 6391, 6379, InstanceStatus.REMOVED);
    var live = container(DbType.POSTGRESQL, "db", "16", 5544, 5432, InstanceStatus.RUNNING);

    when(instanceService.listAll()).thenReturn(List.of(system, template, removed, live));
    when(docker.resolveEnv(live.getConfig())).thenReturn(List.of("POSTGRES_USER=postgres"));

    String yaml = service().exportYaml();

    assertThat(yaml).contains("  db:");
    assertThat(yaml).doesNotContain("system:").doesNotContain("tmpl:").doesNotContain("gone:");
  }

  @Test
  void neo4j_publishes_the_bolt_port_in_addition_to_the_primary_port() {
    var neo = container(DbType.NEO4J, "graph", "5.26", 7474, 7474, InstanceStatus.RUNNING);
    when(instanceService.listAll()).thenReturn(List.of(neo));
    when(docker.resolveEnv(neo.getConfig())).thenReturn(List.of("NEO4J_AUTH=neo4j/secret"));

    String yaml = service().exportYaml();

    assertThat(yaml).contains("      - \"7474:7474\"").contains("      - \"7687:7687\"");
  }

  @Test
  void empty_when_no_exportable_instances() {
    when(instanceService.listAll()).thenReturn(List.of());

    String yaml = service().exportYaml();

    assertThat(yaml).contains("services: {}").doesNotContain("\nvolumes:");
  }

  @Test
  void deduplicates_multiple_containers_of_the_same_config_preferring_running() {
    var stopped = container(DbType.REDIS, "cache", "7.4", 6390, 6379, InstanceStatus.STOPPED);
    var running = container(DbType.REDIS, "cache", "7.4", 6390, 6379, InstanceStatus.RUNNING);
    // Same config id so they collapse to one service.
    running.setConfig(stopped.getConfig());

    when(instanceService.listAll()).thenReturn(List.of(stopped, running));
    when(docker.resolveEnv(stopped.getConfig())).thenReturn(List.of());

    String yaml = service().exportYaml();

    // Only one "cache:" service block.
    assertThat(yaml.split("  cache:", -1)).hasSize(2); // split yields n+1 parts for n occurrences
  }

  @Test
  void configIds_filter_restricts_export_to_the_selected_instances() {
    var cache = container(DbType.REDIS, "cache", "7.4", 6390, 6379, InstanceStatus.RUNNING);
    var db = container(DbType.POSTGRESQL, "db", "16", 5544, 5432, InstanceStatus.RUNNING);
    when(instanceService.listAll()).thenReturn(List.of(cache, db));
    when(docker.resolveEnv(cache.getConfig())).thenReturn(List.of());

    String yaml = service().exportYaml(List.of(cache.getConfig().getId()));

    assertThat(yaml).contains("  cache:").doesNotContain("  db:");
  }

  @Test
  void null_or_empty_configIds_exports_all_instances() {
    var cache = container(DbType.REDIS, "cache", "7.4", 6390, 6379, InstanceStatus.RUNNING);
    var db = container(DbType.POSTGRESQL, "db", "16", 5544, 5432, InstanceStatus.RUNNING);
    when(instanceService.listAll()).thenReturn(List.of(cache, db));
    when(docker.resolveEnv(cache.getConfig())).thenReturn(List.of());
    when(docker.resolveEnv(db.getConfig())).thenReturn(List.of());

    assertThat(service().exportYaml((List<String>) null)).contains("  cache:").contains("  db:");
    assertThat(service().exportYaml(List.of())).contains("  cache:").contains("  db:");
  }

  @Test
  void unknown_configId_is_silently_ignored() {
    var cache = container(DbType.REDIS, "cache", "7.4", 6390, 6379, InstanceStatus.RUNNING);
    when(instanceService.listAll()).thenReturn(List.of(cache));

    String yaml = service().exportYaml(List.of("does-not-exist"));

    assertThat(yaml).contains("services: {}").doesNotContain("  cache:");
  }

  @Test
  void non_docker_deploy_method_is_excluded_even_when_selected() {
    var brewed = container(DbType.POSTGRESQL, "brewed", "16", 5433, 5432, InstanceStatus.RUNNING);
    brewed.getConfig().setDeployMethod(DeployMethod.HOMEBREW);
    var docked = container(DbType.POSTGRESQL, "docked", "16", 5434, 5432, InstanceStatus.RUNNING);
    docked.getConfig().setDeployMethod(DeployMethod.DOCKER);
    var legacy = container(DbType.POSTGRESQL, "legacy", "16", 5435, 5432, InstanceStatus.RUNNING);
    // deployMethod left null — legacy rows default to DOCKER.

    when(instanceService.listAll()).thenReturn(List.of(brewed, docked, legacy));
    when(docker.resolveEnv(docked.getConfig())).thenReturn(List.of());
    when(docker.resolveEnv(legacy.getConfig())).thenReturn(List.of());

    String yaml = service().exportYaml();

    assertThat(yaml).contains("  docked:").contains("  legacy:").doesNotContain("  brewed:");
  }

  @Test
  void resolveInstanceName_finds_the_config_name_by_id() {
    var cache = container(DbType.REDIS, "cache", "7.4", 6390, 6379, InstanceStatus.RUNNING);
    when(instanceService.listAll()).thenReturn(List.of(cache));

    assertThat(service().resolveInstanceName(cache.getConfig().getId())).contains("cache");
    assertThat(service().resolveInstanceName("does-not-exist")).isEqualTo(Optional.empty());
  }
}
