package com.dbdeployer.ai.tools;

import com.dbdeployer.api.dto.ConfigTemplateRequest;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.DeploymentResponse;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.runtime.OllamaModelPuller;
import com.dbdeployer.service.ConfigTemplateService;
import com.dbdeployer.service.DbInstanceService;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The agent's hands. Each {@code @Tool} method is a <b>thin wrapper</b> over the existing {@link
 * DbInstanceService} / {@link ConfigTemplateService} / {@link DockerDeployEngine} / {@link
 * OllamaModelPuller} — no new business logic — so the agent executes the same battle-tested code
 * paths (validation, pipeline, recovery) the UI uses (roadmap §4.4). The same object is exposed to
 * the in-app agent and to the MCP server.
 *
 * <p>Classification (roadmap §5) — the policy lives in {@link AgentSafety}, keyed by method name:
 *
 * <ul>
 *   <li><b>read-only</b> — {@code listInstances}, {@code readLogs}, {@code connectionConfig},
 *       {@code stackSummary} — never gated.
 *   <li><b>write</b> — {@code deployDatabase}, {@code createKafkaTopic}, {@code pullModel} —
 *       state-changing but non-destructive; confirmation-gated by the chat layer's manual
 *       tool-execution loop and stripped entirely in read-only mode.
 *   <li><b>destructive</b> — {@code stopInstance}, {@code removeInstance} — confirmation-gated.
 * </ul>
 */
@Slf4j
@Component
public class InfrastructureTools {

  /** Cap on in-container {@code docker exec} calls (e.g. Kafka topic creation). */
  private static final int EXEC_TIMEOUT_SECONDS = 20;

  /** Kafka's in-container broker listener — see {@code DatabaseCatalog} KAFKA definition. */
  private static final String KAFKA_BOOTSTRAP = "localhost:9092";

  /** Topic admin tool inside the {@code apache/kafka} image. */
  private static final String KAFKA_TOPICS_BIN = "/opt/kafka/bin/kafka-topics.sh";

  private final DbInstanceService service;
  private final ConnectionStringBuilder connBuilder;
  private final ConfigTemplateService configTemplate;
  private final DockerDeployEngine docker;
  private final OllamaModelPuller modelPuller;
  private final String ollamaBaseUrl;

  public InfrastructureTools(
      DbInstanceService service,
      ConnectionStringBuilder connBuilder,
      ConfigTemplateService configTemplate,
      DockerDeployEngine docker,
      OllamaModelPuller modelPuller,
      @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
    this.service = service;
    this.connBuilder = connBuilder;
    this.configTemplate = configTemplate;
    this.docker = docker;
    this.modelPuller = modelPuller;
    this.ollamaBaseUrl = ollamaBaseUrl;
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

  // ── Write (confirmation-gated by the chat layer) ─────────────────────────────

  @Tool(
      description =
          "Deploy a new database or service instance (e.g. POSTGRESQL, REDIS, MONGODB, KAFKA)."
              + " Runs the same validated, async deploy pipeline as the UI. Leave version blank for"
              + " the recommended default and pass hostPort=0 to use the service's default port.")
  public String deployDatabase(
      @ToolParam(description = "service type, e.g. POSTGRESQL, REDIS, MONGODB, KAFKA") String type,
      @ToolParam(description = "a unique name for the new instance") String name,
      @ToolParam(description = "image version/tag; blank for the recommended default")
          String version,
      @ToolParam(description = "host port to publish; 0 to use the service default port")
          int hostPort) {
    DbType dbType = parseDbType(type);
    DatabaseCatalog.DbDefinition def = DatabaseCatalog.get(dbType);
    if (def == null) {
      throw new IllegalArgumentException("Unsupported service type: " + type);
    }
    String resolvedVersion =
        (version == null || version.isBlank())
            ? (def.versions().isEmpty() ? "latest" : def.versions().get(0))
            : version.trim();
    int resolvedPort = hostPort > 0 ? hostPort : def.defaultPort();

    ConfigTemplateRequest req =
        new ConfigTemplateRequest(
            name,
            "Deployed by the Port Wrangler agent",
            dbType,
            resolvedVersion,
            resolvedPort,
            null,
            null,
            null,
            null);

    // Same two calls the deploy controller makes — full validation + pipeline for free.
    DeploymentConfig config = configTemplate.create(req);
    DeploymentResponse response = service.deploy(req, config, false);
    String pipelineId =
        response.getDeployedContainer() != null
            ? response.getDeployedContainer().getLatestPipelineId()
            : null;

    log.info(
        "[agent] deploy '{}' ({} {}) on port {} (pipeline {})",
        name,
        dbType,
        resolvedVersion,
        resolvedPort,
        pipelineId);
    return "Deploying '%s' (%s %s) on host port %d. The deploy pipeline has started; the instance"
            .formatted(name, dbType, resolvedVersion, resolvedPort)
        + " will move from DEPLOYING to RUNNING. Use listInstances to check its status.";
  }

  @Tool(
      description =
          "Create a Kafka topic on a running KAFKA instance, by instance name. Pass partitions=0"
              + " for a single partition.")
  public String createKafkaTopic(
      @ToolParam(description = "the KAFKA instance name") String instanceName,
      @ToolParam(description = "the topic name to create") String topic,
      @ToolParam(description = "number of partitions; 0 for a single partition") int partitions) {
    if (topic == null || topic.isBlank()) {
      throw new IllegalArgumentException("Topic name is required");
    }
    DeployedContainer container = requireByName(instanceName);
    DeploymentConfig config = container.getConfig();
    if (config.getDbType() != DbType.KAFKA) {
      throw new IllegalArgumentException(
          "Instance '"
              + instanceName
              + "' is not a Kafka instance (it is "
              + config.getDbType()
              + ")");
    }
    if (container.getContainerId() == null) {
      throw new IllegalStateException(
          "Kafka instance '" + instanceName + "' has no running container");
    }
    int parts = partitions > 0 ? partitions : 1;
    String[] cmd = {
      KAFKA_TOPICS_BIN,
      "--create",
      "--topic",
      topic.trim(),
      "--bootstrap-server",
      KAFKA_BOOTSTRAP,
      "--partitions",
      String.valueOf(parts),
      "--replication-factor",
      "1"
    };

    String out = docker.execCapture(container.getContainerId(), cmd, EXEC_TIMEOUT_SECONDS);
    if (out == null) {
      throw new IllegalStateException(
          "Failed to create topic '"
              + topic
              + "' on '"
              + instanceName
              + "' — the command exited non-zero (the topic may already exist).");
    }
    log.info(
        "[agent] created Kafka topic '{}' ({} partitions) on '{}'", topic, parts, instanceName);
    String detail = out.isBlank() ? "" : "\n" + out.trim();
    return "Created topic '%s' (%d partition(s)) on Kafka instance '%s'.%s"
        .formatted(topic.trim(), parts, instanceName, detail);
  }

  @Tool(
      description =
          "Pull an LLM model (e.g. 'llama3.1:8b' or 'qwen2.5:7b') into the local Ollama runtime so"
              + " it can be used for chat and model comparison. Blocks until the pull completes.")
  public String pullModel(
      @ToolParam(description = "the Ollama model tag, e.g. 'llama3.1:8b'") String modelTag) {
    if (modelTag == null || modelTag.isBlank()) {
      throw new IllegalArgumentException("Model tag is required");
    }
    OllamaModelPuller.PullResult result = modelPuller.pull(ollamaBaseUrl, modelTag.trim());
    log.info("[agent] pull '{}' into {} -> success={}", modelTag, ollamaBaseUrl, result.success());
    if (!result.success()) {
      throw new IllegalStateException(result.message());
    }
    return result.message();
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

  private DbType parseDbType(String type) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Service type is required");
    }
    try {
      return DbType.valueOf(type.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown service type: " + type);
    }
  }

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
