package com.dbdeployer.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.dbdeployer.api.dto.ConfigTemplateRequest;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployMethod;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.service.DbInstanceService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test: verifies that the 4-step deploy pipeline (ImagePull → ContainerCreate →
 * ContainerStart → Finalise) transitions correctly through PENDING → RUNNING → SUCCESS against a
 * real Docker socket and a Testcontainers-managed system DB.
 *
 * <p>SystemDbProvisioner is bypassed (auto-provision=false); the Testcontainers PostgreSQL
 * container substitutes as the system DB and Liquibase runs V1–V5 migrations on it.
 */
// disabledWithoutDocker=true: skip gracefully when Docker is unavailable to Testcontainers
// (e.g. Docker Desktop with Enhanced Container Isolation enabled). CI always has Docker.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DeployPipelineIT {

  @Container
  static final PostgreSQLContainer<?> systemDb =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("dbdeployer")
          .withUsername("dbdeployer")
          .withPassword("dbdeployer_internal");

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", systemDb::getJdbcUrl);
    r.add("spring.datasource.username", systemDb::getUsername);
    r.add("spring.datasource.password", systemDb::getPassword);
    // Skip Docker-managed system DB; this test is the system DB.
    r.add("dbdeployer.system-db.auto-provision", () -> "false");
    // Prevent image-tracking scheduler from hitting Docker Hub during the test.
    r.add("dbdeployer.image-validation.scheduler-enabled", () -> "false");
  }

  @Autowired private DbInstanceService instanceService;
  @Autowired private DeploymentPipelineRepository pipelineRepo;

  private String deployedConfigId;

  @AfterEach
  void removeDeployedContainer() {
    if (deployedConfigId != null) {
      try {
        instanceService.removeInstance(deployedConfigId);
      } catch (Exception ignored) {
        // best-effort cleanup; test outcome is unaffected
      }
    }
  }

  @Test
  void deploy_redis_pipeline_transitions_to_SUCCESS() throws InterruptedException {
    String name = "it-redis-" + UUID.randomUUID().toString().substring(0, 6);

    var config = new DeploymentConfig();
    config.setId(UUID.randomUUID().toString());
    config.setName(name);
    config.setDbType(DbType.REDIS);
    config.setVersion("7");
    config.setHostPort(16391);
    config.setDeployMethod(DeployMethod.DOCKER);

    var req =
        new ConfigTemplateRequest(name, null, DbType.REDIS, "7", 16391, null, null, null, null);

    var response = instanceService.deploy(req, config, false);
    deployedConfigId = response.getDeploymentConfig().getId();
    String pipelineId = response.getDeployedContainer().getLatestPipelineId();

    DeploymentPipeline pipeline = awaitPipeline(pipelineId, Duration.ofSeconds(120));

    assertThat(pipeline.getStatus())
        .as("pipeline %s should succeed; check Docker logs on failure", pipelineId)
        .isEqualTo(PipelineStatus.SUCCESS);
  }

  private DeploymentPipeline awaitPipeline(String pipelineId, Duration timeout)
      throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      var p = pipelineRepo.findById(pipelineId).orElseThrow();
      var status = p.getStatus();
      if (status != PipelineStatus.PENDING && status != PipelineStatus.RUNNING) {
        return p;
      }
      Thread.sleep(1_000);
    }
    fail("Pipeline %s did not complete within %s".formatted(pipelineId, timeout));
    throw new IllegalStateException(); // unreachable
  }
}
