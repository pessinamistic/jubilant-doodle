package com.dbdeployer.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeploymentConfig;
import org.junit.jupiter.api.Test;

class ConnectionStringBuilderTest {

  private final ConnectionStringBuilder builder = new ConnectionStringBuilder();

  private static DeploymentConfig config(DbType type, int port) {
    var c = new DeploymentConfig();
    c.setDbType(type);
    c.setVersion("16");
    c.setHostPort(port);
    return c;
  }

  @Test
  void springConfig_postgres_emits_jdbc_datasource_block() {
    var c = config(DbType.POSTGRESQL, 5544);
    c.setUsername("alice");
    c.setPassword("s3cret");
    c.setDatabaseName("orders");

    String props = builder.springBootProperties(c);

    assertThat(props)
        .contains("spring.datasource.url=jdbc:postgresql://localhost:5544/orders")
        .contains("spring.datasource.username=alice")
        .contains("spring.datasource.password=s3cret")
        .contains("spring.datasource.driver-class-name=org.postgresql.Driver")
        .contains("org.postgresql:postgresql");
  }

  @Test
  void springConfig_postgres_falls_back_to_catalog_placeholders_when_blank() {
    var c = config(DbType.POSTGRESQL, 5432);

    String props = builder.springBootProperties(c);

    // POSTGRES_USER placeholder is "postgres"; DB falls back to "mydb".
    assertThat(props)
        .contains("spring.datasource.username=postgres")
        .contains("jdbc:postgresql://localhost:5432/mydb");
  }

  @Test
  void springConfig_redis_emits_spring_data_redis_keys() {
    var c = config(DbType.REDIS, 6390);
    c.setPassword("redispw");

    String props = builder.springBootProperties(c);

    assertThat(props)
        .contains("spring.data.redis.host=localhost")
        .contains("spring.data.redis.port=6390")
        .contains("spring.data.redis.password=redispw");
  }

  @Test
  void springConfig_mongo_emits_uri_with_authsource() {
    var c = config(DbType.MONGODB, 27018);
    c.setUsername("admin");
    c.setPassword("pw");
    c.setDatabaseName("app");

    String props = builder.springBootProperties(c);

    assertThat(props)
        .contains(
            "spring.data.mongodb.uri=mongodb://admin:pw@localhost:27018/app?authSource=admin");
  }

  @Test
  void springConfig_kafka_emits_bootstrap_servers() {
    var c = config(DbType.KAFKA, 9094);

    String props = builder.springBootProperties(c);

    assertThat(props).contains("spring.kafka.bootstrap-servers=localhost:9094");
  }

  @Test
  void springConfig_nonDatasourceType_falls_back_to_commented_base_url() {
    var c = config(DbType.GRAFANA, 3001);

    String props = builder.springBootProperties(c);

    // Grafana has no Spring datasource — output is a comment, never raw properties.
    assertThat(props).startsWith("#").contains("Grafana");
  }

  @Test
  void build_still_works_after_refactor() {
    var c = config(DbType.POSTGRESQL, 5432);
    c.setUsername("u");
    c.setPassword("p");
    c.setDatabaseName("d");

    assertThat(builder.build(c)).isEqualTo("postgresql://u:p@localhost:5432/d");
    assertThat(builder.buildMasked(c)).isEqualTo("postgresql://u:****@localhost:5432/d");
  }
}
