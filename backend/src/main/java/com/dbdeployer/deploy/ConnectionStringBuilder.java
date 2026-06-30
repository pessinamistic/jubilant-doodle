package com.dbdeployer.deploy;

import com.dbdeployer.model.DeploymentConfig;
import org.springframework.stereotype.Component;

@Component
public class ConnectionStringBuilder {

  public String build(DeploymentConfig config) {
    var def = DatabaseCatalog.get(config.getDbType());
    if (def == null) return "N/A";

    ResolvedCreds c = resolve(config, def);

    return def.connectionStringTemplate()
        .replace("{username}", c.username())
        .replace("{password}", c.password())
        .replace("{port}", String.valueOf(config.getHostPort()))
        .replace("{database}", c.database());
  }

  /** Returns a masked version safe for display (password replaced with ****) */
  public String buildMasked(DeploymentConfig config) {
    return build(config).replaceAll(":[^@:/]+@", ":****@");
  }

  /**
   * Returns a ready-to-paste Spring Boot {@code application.properties} block for the given
   * instance, using the credentials/port already stored in the config. For relational stores this
   * emits {@code spring.datasource.*} (JDBC URL + driver). For document/cache/search/messaging
   * stores it emits the matching {@code spring.data.*} / {@code spring.kafka.*} keys. Service types
   * without a first-class Spring Boot starter fall back to a commented base URL.
   *
   * <p>Reuses {@link #resolve(DeploymentConfig, DatabaseCatalog.DbDefinition)} so the resolved
   * username/password/database exactly match {@link #build(DeploymentConfig)} and the deployed
   * container's environment.
   */
  public String springBootProperties(DeploymentConfig config) {
    var def = DatabaseCatalog.get(config.getDbType());
    if (def == null) {
      return "# No Spring configuration available for this service type.";
    }
    ResolvedCreds c = resolve(config, def);
    int port = config.getHostPort();
    String db = c.database().isBlank() ? "mydb" : c.database();

    return switch (config.getDbType()) {
      case POSTGRESQL ->
          jdbcBlock(
              "jdbc:postgresql://localhost:%d/%s".formatted(port, db),
              c.username(),
              c.password(),
              "org.postgresql.Driver",
              "org.postgresql:postgresql");
      case MYSQL ->
          jdbcBlock(
              "jdbc:mysql://localhost:%d/%s".formatted(port, db),
              c.username(),
              c.password(),
              "com.mysql.cj.jdbc.Driver",
              "com.mysql:mysql-connector-j");
      case MARIADB ->
          jdbcBlock(
              "jdbc:mariadb://localhost:%d/%s".formatted(port, db),
              c.username(),
              c.password(),
              "org.mariadb.jdbc.Driver",
              "org.mariadb.jdbc:mariadb-java-client");
      case MSSQL ->
          jdbcBlock(
              "jdbc:sqlserver://localhost:%d;databaseName=%s;encrypt=false".formatted(port, db),
              "sa",
              c.password(),
              "com.microsoft.sqlserver.jdbc.SQLServerDriver",
              "com.microsoft.sqlserver:mssql-jdbc");
      case CLICKHOUSE ->
          jdbcBlock(
              // ClickHouse JDBC uses the HTTP interface on 8123 (mapped alongside the native port).
              "jdbc:clickhouse://localhost:8123/%s".formatted(db),
              c.username(),
              c.password(),
              "com.clickhouse.jdbc.ClickHouseDriver",
              "com.clickhouse:clickhouse-jdbc");
      case H2 ->
          """
          # H2 is Port Wrangler's embedded system database — not intended for application use.
          spring.datasource.url=jdbc:h2:file:./data/dbdeployer
          spring.datasource.driver-class-name=org.h2.Driver""";
      case MONGODB ->
          """
          # Requires: org.springframework.boot:spring-boot-starter-data-mongodb
          spring.data.mongodb.uri=mongodb://%s:%s@localhost:%d/%s?authSource=admin"""
              .formatted(c.username(), c.password(), port, db);
      case REDIS ->
          """
          # Requires: org.springframework.boot:spring-boot-starter-data-redis
          spring.data.redis.host=localhost
          spring.data.redis.port=%d
          spring.data.redis.password=%s"""
              .formatted(port, c.password());
      case CASSANDRA ->
          """
          # Requires: org.springframework.boot:spring-boot-starter-data-cassandra
          spring.cassandra.contact-points=localhost
          spring.cassandra.port=%d
          spring.cassandra.local-datacenter=datacenter1
          spring.cassandra.username=%s
          spring.cassandra.password=%s
          spring.cassandra.keyspace-name=%s"""
              .formatted(port, c.username(), c.password(), db);
      case ELASTICSEARCH ->
          """
          # Requires: org.springframework.boot:spring-boot-starter-data-elasticsearch
          spring.elasticsearch.uris=http://localhost:%d
          spring.elasticsearch.username=elastic
          spring.elasticsearch.password=%s"""
              .formatted(port, c.password());
      case NEO4J ->
          """
          # Requires: org.springframework.boot:spring-boot-starter-data-neo4j
          spring.neo4j.uri=bolt://localhost:7687
          spring.neo4j.authentication.username=neo4j
          spring.neo4j.authentication.password=%s"""
              .formatted(c.password().isBlank() ? "secret" : c.password());
      case RABBITMQ ->
          """
          # Requires: org.springframework.boot:spring-boot-starter-amqp
          spring.rabbitmq.host=localhost
          spring.rabbitmq.port=%d
          spring.rabbitmq.username=%s
          spring.rabbitmq.password=%s"""
              .formatted(port, c.username(), c.password());
      case KAFKA ->
          """
          # Requires: org.springframework.boot:spring-boot-starter-kafka
          spring.kafka.bootstrap-servers=localhost:%d"""
              .formatted(port);
      default ->
          "# %s exposes no first-class Spring Boot datasource. Base URL:%n# %s"
              .formatted(def.displayName(), build(config));
    };
  }

  private static String jdbcBlock(
      String url, String username, String password, String driver, String dependency) {
    return """
        # Requires dependency: %s
        spring.datasource.url=%s
        spring.datasource.username=%s
        spring.datasource.password=%s
        spring.datasource.driver-class-name=%s"""
        .formatted(dependency, url, username, password, driver);
  }

  /** Resolved, displayable credentials for a config — falls back to catalog placeholders. */
  private record ResolvedCreds(String username, String password, String database) {}

  private static ResolvedCreds resolve(DeploymentConfig config, DatabaseCatalog.DbDefinition def) {
    String username =
        coalesce(config.getUsername(), firstPlaceholder(def, DatabaseCatalog.EnvVarType.TEXT), "");
    String password =
        coalesce(
            config.getPassword(), firstPlaceholder(def, DatabaseCatalog.EnvVarType.PASSWORD), "");
    String database =
        coalesce(
            config.getDatabaseName(),
            firstPlaceholder(def, DatabaseCatalog.EnvVarType.DATABASE),
            "");
    return new ResolvedCreds(username, password, database);
  }

  private static String firstPlaceholder(
      DatabaseCatalog.DbDefinition def, DatabaseCatalog.EnvVarType type) {
    return def.credentialEnvVars().stream()
        .filter(ev -> ev.type() == type)
        .map(DatabaseCatalog.EnvVar::placeholder)
        .findFirst()
        .orElse("");
  }

  @SafeVarargs
  private static <T extends CharSequence> String coalesce(T... candidates) {
    for (T c : candidates) {
      if (c != null && !c.toString().isBlank()) return c.toString();
    }
    return "";
  }
}
