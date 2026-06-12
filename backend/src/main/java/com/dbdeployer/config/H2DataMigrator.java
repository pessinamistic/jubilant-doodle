package com.dbdeployer.config;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/**
 * One-shot H2 → Postgres data migration runner.
 *
 * <p>
 * Only active when {@code app.migrate-from-h2=true}. Reads every row from the
 * five live tables in the source H2 file and inserts them into the
 * already-migrated Postgres schema via the primary {@link JdbcTemplate}.
 *
 * <p>
 * Idempotent: each target table is skipped if it already contains rows.
 *
 * <p>
 * <b>Usage</b> — run the app once with:
 *
 * <pre>
 *   APP_MIGRATE_FROM_H2=true \
 *   APP_H2_SOURCE_PATH=~/.db-deployer/system/dbdeployer \
 *   ./gradlew bootRun
 * </pre>
 *
 * <p>
 * After verifying that all data landed in Postgres:
 *
 * <ol>
 * <li>Delete this file
 * <li>Remove {@code runtimeOnly("com.h2database:h2")} from
 * {@code build.gradle.kts}
 * </ol>
 */
@Slf4j
@Component
@Order(2) // runs after SystemDbRegistrar (Order 1)
@ConditionalOnProperty(name = "app.migrate-from-h2", havingValue = "true")
public class H2DataMigrator implements ApplicationRunner {

  private final JdbcTemplate pgJdbc;

  @Value("${app.h2-source-path:${user.home}/.db-deployer/system/dbdeployer}")
  private String h2SourcePath;

  public H2DataMigrator(JdbcTemplate pgJdbc) {
    this.pgJdbc = pgJdbc;
  }

  @Override
  public void run(
    ApplicationArguments args) {
    log.info("H2DataMigrator — starting one-shot migration from H2: {}", h2SourcePath);

    JdbcTemplate h2 = buildH2JdbcTemplate();

    migrateDeploymentConfig(h2);
    migrateDeployedContainer(h2);
    migrateDeploymentPipeline(h2);
    migratePipelineStep(h2);
    migrateImageTrackingStatus(h2);

    log.info("H2DataMigrator — migration complete. "
        + "Restart WITHOUT app.migrate-from-h2=true to resume normal operation. "
        + "Once confirmed, delete H2DataMigrator.java and remove the H2 runtimeOnly dep.");
  }

  // ── Table migrations ──────────────────────────────────────────────────────

  private void migrateDeploymentConfig(
    JdbcTemplate h2) {
    if (targetHasRows("deployment_config"))
      return;
    List<Map<String, Object>> rows = h2.queryForList("SELECT * FROM deployment_config");
    for (Map<String, Object> r : rows) {
      pgJdbc.update("""
          INSERT INTO deployment_config
            (id, name, db_type, version, host_port, container_port,
             username, password, database_name, extra_env_json,
             deploy_method, is_system, is_imported, created_at, updated_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          ON CONFLICT (id) DO NOTHING
          """, str(r, "ID"), str(r, "NAME"), str(r, "DB_TYPE"), str(r, "VERSION"), intVal(r, "HOST_PORT"),
          intVal(r, "CONTAINER_PORT"), str(r, "USERNAME"), str(r, "PASSWORD"), str(r, "DATABASE_NAME"),
          str(r, "EXTRA_ENV_JSON"), str(r, "DEPLOY_METHOD"), boolVal(r, "IS_SYSTEM"), boolVal(r, "IS_IMPORTED"),
          ts(r, "CREATED_AT"), ts(r, "UPDATED_AT"));
    }
    log.info("  deployment_config  → {} rows migrated", rows.size());
  }

  private void migrateDeployedContainer(
    JdbcTemplate h2) {
    if (targetHasRows("deployed_container"))
      return;
    List<Map<String, Object>> rows = h2.queryForList("SELECT * FROM deployed_container");
    for (Map<String, Object> r : rows) {
      pgJdbc.update("""
          INSERT INTO deployed_container
            (id, config_id, container_id, container_name, status,
             data_directory, started_at, removed_at, latest_pipeline_id,
             created_at, updated_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?)
          ON CONFLICT (id) DO NOTHING
          """, str(r, "ID"), str(r, "CONFIG_ID"), str(r, "CONTAINER_ID"), str(r, "CONTAINER_NAME"), str(r, "STATUS"),
          str(r, "DATA_DIRECTORY"), ts(r, "STARTED_AT"), ts(r, "REMOVED_AT"), str(r, "LATEST_PIPELINE_ID"),
          ts(r, "CREATED_AT"), ts(r, "UPDATED_AT"));
    }
    log.info("  deployed_container → {} rows migrated", rows.size());
  }

  private void migrateDeploymentPipeline(
    JdbcTemplate h2) {
    if (targetHasRows("deployment_pipeline"))
      return;
    List<Map<String, Object>> rows = h2.queryForList("SELECT * FROM deployment_pipeline");
    for (Map<String, Object> r : rows) {
      pgJdbc.update("""
          INSERT INTO deployment_pipeline
            (id, config_id, status, error_code, error_message,
             created_at, started_at, completed_at)
          VALUES (?,?,?,?,?,?,?,?)
          ON CONFLICT (id) DO NOTHING
          """, str(r, "ID"), str(r, "CONFIG_ID"), str(r, "STATUS"), str(r, "ERROR_CODE"), str(r, "ERROR_MESSAGE"),
          ts(r, "CREATED_AT"), ts(r, "STARTED_AT"), ts(r, "COMPLETED_AT"));
    }
    log.info("  deployment_pipeline → {} rows migrated", rows.size());
  }

  private void migratePipelineStep(
    JdbcTemplate h2) {
    if (targetHasRows("pipeline_step"))
      return;
    List<Map<String, Object>> rows = h2.queryForList("SELECT * FROM pipeline_step");
    for (Map<String, Object> r : rows) {
      pgJdbc.update("""
          INSERT INTO pipeline_step
            (id, pipeline_id, step_type, step_order, status,
             message, started_at, completed_at)
          VALUES (?,?,?,?,?,?,?,?)
          ON CONFLICT (id) DO NOTHING
          """, str(r, "ID"), str(r, "PIPELINE_ID"), str(r, "STEP_TYPE"), intVal(r, "STEP_ORDER"), str(r, "STATUS"),
          str(r, "MESSAGE"), ts(r, "STARTED_AT"), ts(r, "COMPLETED_AT"));
    }
    log.info("  pipeline_step      → {} rows migrated", rows.size());
  }

  private void migrateImageTrackingStatus(
    JdbcTemplate h2) {
    if (targetHasRows("image_tracking_status"))
      return;
    List<Map<String, Object>> rows = h2.queryForList("SELECT * FROM image_tracking_status");
    for (Map<String, Object> r : rows) {
      pgJdbc.update("""
          INSERT INTO image_tracking_status
            (id, db_type, image_name, image_tag, docker_hub_managed,
             local_status, docker_hub_status, decision, message,
             local_checked_at, docker_hub_checked_at, created_at, updated_at)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
          ON CONFLICT (id) DO NOTHING
          """, str(r, "ID"), str(r, "DB_TYPE"), str(r, "IMAGE_NAME"), str(r, "IMAGE_TAG"),
          boolVal(r, "DOCKER_HUB_MANAGED"), str(r, "LOCAL_STATUS"), str(r, "DOCKER_HUB_STATUS"), str(r, "DECISION"),
          str(r, "MESSAGE"), ts(r, "LOCAL_CHECKED_AT"), ts(r, "DOCKER_HUB_CHECKED_AT"), ts(r, "CREATED_AT"),
          ts(r, "UPDATED_AT"));
    }
    log.info("  image_tracking_status → {} rows migrated", rows.size());
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private boolean targetHasRows(
    String table) {
    Integer count = pgJdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    if (count != null && count > 0) {
      log.info("  {} already has {} rows — skipping", table, count);
      return true;
    }
    return false;
  }

  private JdbcTemplate buildH2JdbcTemplate() {
    // Resolve ~ to the actual home directory
    String resolvedPath = h2SourcePath.replace("~", System.getProperty("user.home"));
    String url = "jdbc:h2:file:" + resolvedPath + ";DB_CLOSE_ON_EXIT=FALSE;ACCESS_MODE_DATA=r";
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.h2.Driver");
    ds.setUrl(url);
    ds.setUsername("dbdeployer");
    ds.setPassword("dbdeployer_internal");
    log.info("H2DataMigrator — source H2 URL: {}", url);
    return new JdbcTemplate(ds);
  }

  private static String str(
    Map<String, Object> row,
    String col) {
    Object v = row.get(col);
    return v != null ? v.toString() : null;
  }

  private static int intVal(
    Map<String, Object> row,
    String col) {
    Object v = row.get(col);
    if (v == null)
      return 0;
    return ((Number) v).intValue();
  }

  private static boolean boolVal(
    Map<String, Object> row,
    String col) {
    Object v = row.get(col);
    if (v instanceof Boolean b)
      return b;
    if (v instanceof Number n)
      return n.intValue() != 0;
    return Boolean.parseBoolean(String.valueOf(v));
  }

  private static Timestamp ts(
    Map<String, Object> row,
    String col) {
    Object v = row.get(col);
    if (v == null)
      return null;
    if (v instanceof Timestamp t)
      return t;
    // H2 may return java.time types
    if (v instanceof java.time.LocalDateTime ldt)
      return Timestamp.valueOf(ldt);
    if (v instanceof java.time.Instant i)
      return Timestamp.from(i);
    return null;
  }
}
