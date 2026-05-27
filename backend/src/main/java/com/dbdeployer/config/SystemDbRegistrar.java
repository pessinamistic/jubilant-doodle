package com.dbdeployer.config;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs at startup ({@code @Order(1)}). Upserts the system Postgres database into the {@code
 * deployment_config} / {@code deployed_container} tables so it appears in the UI as a read-only
 * SYSTEM entry with live metadata (Postgres version, etc.). Uses a fixed config ID ("system") so
 * subsequent starts simply update the row.
 */
@Component
@Order(1)
public class SystemDbRegistrar implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SystemDbRegistrar.class);
  static final String SYSTEM_CONFIG_ID = "system";

  private final DeploymentConfigRepository configRepo;
  private final DeployedContainerRepository containerRepo;
  private final JdbcTemplate jdbc;

  @Value("${dbdeployer.system-db.host-port:5499}")
  private int systemDbHostPort;

  @Value("${dbdeployer.system-db.container-name:dbdeployer-system-db}")
  private String systemDbContainerName;

  public SystemDbRegistrar(
      DeploymentConfigRepository configRepo,
      DeployedContainerRepository containerRepo,
      JdbcTemplate jdbc) {
    this.configRepo = configRepo;
    this.containerRepo = containerRepo;
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      // Parse a short version string from Postgres' banner, e.g.
      // "PostgreSQL 16.3 on aarch64-unknown-linux-musl, ..." → "16.3"
      String pgBanner = jdbc.queryForObject("SELECT version()", String.class);
      String pgVersion = parsePostgresVersion(pgBanner);

      // ── Upsert DeploymentConfig ──
      DeploymentConfig config = configRepo.findById(SYSTEM_CONFIG_ID).orElse(null);
      if (config == null) {
        config = new DeploymentConfig();
        config.setId(SYSTEM_CONFIG_ID);
      }
      config.setName("System Database");
      config.setDbType(DbType.POSTGRESQL);
      config.setVersion(pgVersion);
      config.setHostPort(systemDbHostPort);
      config.setContainerPort(5432);
      config.setUsername("dbdeployer");
      config.setPassword(null); // not exposed in the UI
      config.setDatabaseName("dbdeployer");
      config.setDeployMethod(DeployMethod.DOCKER);
      config.setSystem(true);
      configRepo.save(config);

      // ── Upsert DeployedContainer ──
      DeployedContainer container = containerRepo.findByConfigId(SYSTEM_CONFIG_ID).orElse(null);
      if (container == null) {
        container = new DeployedContainer();
        container.setId(UUID.randomUUID().toString());
        container.setConfig(config);
      }
      container.setContainerId(null); // not tracked by ID — provisioner manages it
      container.setContainerName(systemDbContainerName);
      container.setStatus(InstanceStatus.RUNNING);
      container.setStartedAt(Instant.now());
      containerRepo.save(container);

      log.info(
          "System DB (PostgreSQL {}, container: {}) registered", pgVersion, systemDbContainerName);

    } catch (Exception e) {
      // Non-fatal: the app should still start even if registration fails
      log.warn("SystemDbRegistrar: failed to register system DB — {}", e.getMessage());
    }
  }

  /**
   * Extracts a short version string from the Postgres {@code version()} banner.
   *
   * <p>Example input: {@code "PostgreSQL 16.3 on aarch64-unknown-linux-musl, compiled by gcc 13.2.1
   * ..."}
   *
   * <p>Returns: {@code "16.3"}
   */
  private static String parsePostgresVersion(String banner) {
    if (banner == null) return "16";
    // Banner starts with "PostgreSQL <major>.<minor>" (optionally followed by more text)
    String[] parts = banner.split("\\s+");
    // parts[0] = "PostgreSQL", parts[1] = "16.3"
    if (parts.length >= 2) return parts[1];
    return "16";
  }
}
