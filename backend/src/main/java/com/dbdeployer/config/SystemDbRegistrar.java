package com.dbdeployer.config;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs at startup ({@code @Order(1)}). Creates the system DB rows once and then
 * only backfills missing runtime metadata (for example container ID) from the
 * provisioner so we do not rewrite the system config on every run.
 */
@Slf4j
@Order(1)
@Component
public class SystemDbRegistrar implements ApplicationRunner {

  static final String SYSTEM_CONFIG_ID = "system";

  private final DeploymentConfigRepository configRepo;
  private final DeployedContainerRepository containerRepo;
  private final JdbcTemplate jdbc;

  @Value("${dbdeployer.system-db.host-port:5499}")
  private int systemDbHostPort;

  @Value("${dbdeployer.system-db.container-name:dbdeployer-system-db}")
  private String systemDbContainerName;

  @Value("${dbdeployer.system-db.auto-provision:true}")
  private boolean autoProvision;

  @Value("${dbdeployer.system-db.runtime.container-id:}")
  private String runtimeContainerId;

  @Value("${dbdeployer.system-db.runtime.container-name:}")
  private String runtimeContainerName;

  @Value(("${dbdeployer.system-db.data-dir}"))
  private String dataDir;

  public SystemDbRegistrar(
    DeploymentConfigRepository configRepo,
    DeployedContainerRepository containerRepo,
    JdbcTemplate jdbc) {
    this.configRepo = configRepo;
    this.containerRepo = containerRepo;
    this.jdbc = jdbc;
  }

  @Override
  public void run(
    ApplicationArguments args) {
    try {
      DeploymentConfig config = configRepo.findById(SYSTEM_CONFIG_ID).orElse(null);
      if (config == null) {
        config = new DeploymentConfig();
        config.setId(SYSTEM_CONFIG_ID);
        config.setName("System Database");
        config.setDbType(DbType.POSTGRESQL);
        config.setVersion(resolvePostgresVersion());
        config.setHostPort(systemDbHostPort);
        config.setUsername("dbdeployer");
        config.setPassword("dbdeployer_internal"); // not exposed in the UI
        config.setDatabaseName("dbdeployer");
        config.setDeployMethod(DeployMethod.DOCKER);
        config.setSystem(true);
        configRepo.save(config);
        log.info("System DB config created (id={})", SYSTEM_CONFIG_ID);
      } else {
        boolean changed = false;
        if (!config.isSystem()) {
          config.setSystem(true);
          changed = true;
        }
        if (config.getDeployMethod() == null) {
          config.setDeployMethod(DeployMethod.DOCKER);
          changed = true;
        }
        if (changed) {
          configRepo.save(config);
          log.info("System DB config backfilled (id={})", SYSTEM_CONFIG_ID);
        }
      }

      DeployedContainer container = containerRepo.findByConfigId(SYSTEM_CONFIG_ID).orElse(null);
      boolean changed = false;
      if (container == null) {
        container = new DeployedContainer();
        container.setId(UUID.randomUUID().toString());
        container.setHostPort(systemDbHostPort);
        container.setContainerPort(5432);
        container.setDataDirectory(dataDir);
        container.setConfig(config);
        changed = true;
      }

      String effectiveContainerName = firstNonBlank(resolveRuntimeContainerName(), systemDbContainerName);
      if (!Objects.equals(container.getContainerName(), effectiveContainerName)) {
        container.setContainerName(effectiveContainerName);
        changed = true;
      }

      String effectiveContainerId = resolveRuntimeContainerId();
      if (effectiveContainerId != null && !effectiveContainerId.isBlank()
          && !Objects.equals(container.getContainerId(), effectiveContainerId)) {
        container.setContainerId(effectiveContainerId);
        changed = true;
      }

      if (autoProvision && container.getStatus() != InstanceStatus.RUNNING) {
        container.setStatus(InstanceStatus.RUNNING);
        changed = true;
      }
      if (container.getStatus() == null) {
        container.setStatus(autoProvision ? InstanceStatus.RUNNING : InstanceStatus.STOPPED);
        changed = true;
      }
      if (autoProvision && container.getStartedAt() == null) {
        container.setStartedAt(Instant.now());
        changed = true;
      }

      if (changed) {
        containerRepo.save(container);
        log.info("System DB container metadata updated (name={}, id={})", container.getContainerName(),
            abbreviate(container.getContainerId()));
      }

    } catch (Exception e) {
      // Non-fatal: app startup should continue even if metadata sync fails
      log.warn("SystemDbRegistrar: failed to register system DB — {}", e.getMessage());
    }
  }

  private String resolveRuntimeContainerId() {
    String fromSystemProperty = System.getProperty(SystemDbProvisioner.RUNTIME_CONTAINER_ID_PROPERTY, "").trim();
    if (!fromSystemProperty.isBlank())
      return fromSystemProperty;
    return runtimeContainerId != null && !runtimeContainerId.isBlank() ? runtimeContainerId : null;
  }

  private String resolveRuntimeContainerName() {
    String fromSystemProperty = System.getProperty(SystemDbProvisioner.RUNTIME_CONTAINER_NAME_PROPERTY, "").trim();
    if (!fromSystemProperty.isBlank())
      return fromSystemProperty;
    return runtimeContainerName != null && !runtimeContainerName.isBlank() ? runtimeContainerName : null;
  }

  private String resolvePostgresVersion() {
    try {
      String pgBanner = jdbc.queryForObject("SELECT version()", String.class);
      return parsePostgresVersion(pgBanner);
    } catch (Exception e) {
      log.warn("SystemDbRegistrar: could not resolve Postgres version, defaulting to 16");
      return "16";
    }
  }

  private static String firstNonBlank(
    String first,
    String second) {
    if (first != null && !first.isBlank())
      return first;
    return second;
  }

  private static String abbreviate(
    String value) {
    if (value == null || value.isBlank())
      return "n/a";
    return value.length() > 12 ? value.substring(0, 12) : value;
  }

  /**
   * Extracts a short version string from the Postgres {@code version()} banner.
   *
   * <p>
   * Example input:
   * {@code "PostgreSQL 16.3 on aarch64-unknown-linux-musl, compiled by gcc 13.2.1
   * ..."}
   *
   * <p>
   * Returns: {@code "16.3"}
   */
  private static String parsePostgresVersion(
    String banner) {
    if (banner == null)
      return "16";
    // Banner starts with "PostgreSQL <major>.<minor>" (optionally followed by more
    // text)
    String[] parts = banner.split("\\s+");
    // parts[0] = "PostgreSQL", parts[1] = "16.3"
    if (parts.length >= 2)
      return parts[1];
    return "16";
  }
}
