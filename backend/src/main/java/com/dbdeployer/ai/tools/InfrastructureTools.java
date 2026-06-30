package com.dbdeployer.ai.tools;

import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.service.DbInstanceService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The agent's hands. Each {@code @Tool} method is a <b>thin wrapper</b> over the existing {@link
 * DbInstanceService} — no new business logic — so the agent executes the same battle-tested code
 * paths (validation, pipeline, recovery) the UI uses (roadmap §4.4). The same object is exposed to
 * the in-app agent and to the MCP server.
 *
 * <p>Classification (roadmap §5):
 *
 * <ul>
 *   <li><b>read-only</b> — {@code listInstances}, {@code readLogs}, {@code connectionConfig},
 *       {@code stackSummary} — never gated.
 *   <li><b>destructive</b> — {@code stopInstance}, {@code removeInstance} — confirmation-gated by
 *       the chat layer's manual tool-execution loop.
 * </ul>
 */
@Slf4j
@Component
public class InfrastructureTools {

  private final DbInstanceService service;
  private final ConnectionStringBuilder connBuilder;

  public InfrastructureTools(DbInstanceService service, ConnectionStringBuilder connBuilder) {
    this.service = service;
    this.connBuilder = connBuilder;
  }

  // ── Read-only ───────────────────────────────────────────────────────────────

  @Tool(description = "List all deployed instances with their status, type, version and host port")
  public List<InstanceSummary> listInstances() {
    return service.listAll().stream()
        .filter(c -> c.getStatus() != InstanceStatus.REMOVED)
        .map(this::toSummary)
        .toList();
  }

  @Tool(description = "Read the last N log lines from a deployed instance, by instance name")
  public String readLogs(
      @ToolParam(description = "the instance name") String instanceName,
      @ToolParam(description = "number of log lines to return") int lines) {
    DeployedContainer container = requireByName(instanceName);
    try {
      return service.getLogs(container.getId(), lines <= 0 ? 100 : lines);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "Interrupted while reading logs for " + instanceName;
    }
  }

  @Tool(
      description =
          "Get the connection string and a ready-to-paste Spring Boot application.properties block"
              + " for an instance, by name")
  public String connectionConfig(
      @ToolParam(description = "the instance name") String instanceName) {
    DeploymentConfig config = requireByName(instanceName).getConfig();
    return "Connection string:\n"
        + connBuilder.build(config)
        + "\n\nSpring config:\n"
        + connBuilder.springBootProperties(config);
  }

  @Tool(
      description =
          "Summarise the whole local stack: every active instance with type, port, status",
      returnDirect = true)
  public StackSummary stackSummary() {
    List<InstanceSummary> active = listInstances();
    return new StackSummary(active.size(), active);
  }

  // ── Destructive (confirmation-gated by the chat layer) ───────────────────────

  @Tool(description = "Stop a running instance by name (does not delete it)")
  public InstanceSummary stopInstance(
      @ToolParam(description = "the instance name") String instanceName) {
    DeployedContainer container = requireByName(instanceName);
    service.stopInstance(container.getId());
    log.info("[agent] stopped instance '{}'", instanceName);
    return toSummary(service.getById(container.getId()));
  }

  @Tool(description = "Remove an instance and its data by name (destructive, irreversible)")
  public String removeInstance(@ToolParam(description = "the instance name") String instanceName) {
    DeployedContainer container = requireByName(instanceName);
    service.removeInstance(container.getId());
    log.info("[agent] removed instance '{}'", instanceName);
    return "Removed instance '" + instanceName + "'.";
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  private DeployedContainer requireByName(String name) {
    return service.listAll().stream()
        .filter(c -> c.getStatus() != InstanceStatus.REMOVED)
        .filter(c -> c.getConfig() != null && c.getConfig().getName().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No active instance named '" + name + "'"));
  }

  private InstanceSummary toSummary(DeployedContainer c) {
    DeploymentConfig config = c.getConfig();
    return new InstanceSummary(
        config.getName(),
        config.getDbType().name(),
        config.getVersion(),
        String.valueOf(c.getStatus()),
        c.getHostPort(),
        connBuilder.buildMasked(config));
  }
}
