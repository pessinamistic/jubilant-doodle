package com.dbdeployer.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InfrastructureToolsTest {

  @Mock private com.dbdeployer.service.DbInstanceService service;
  @Mock private ConnectionStringBuilder connBuilder;

  private InfrastructureTools tools() {
    return new InfrastructureTools(service, connBuilder);
  }

  private static DeployedContainer container(String name, InstanceStatus status) {
    var config = new DeploymentConfig();
    config.setId("cfg-" + name);
    config.setName(name);
    config.setDbType(DbType.POSTGRESQL);
    config.setVersion("16");
    config.setHostPort(5544);
    var c = new DeployedContainer();
    c.setId("inst-" + name);
    c.setConfig(config);
    c.setHostPort(5544);
    c.setStatus(status);
    return c;
  }

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
}
