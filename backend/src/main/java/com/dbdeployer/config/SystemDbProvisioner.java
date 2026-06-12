package com.dbdeployer.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Runs before any Spring bean is created. Ensures the dedicated system Postgres
 * container ("dbdeployer-system-db") is up and accepting connections before
 * HikariCP tries to connect to it. Flow: 1. Connect to Docker daemon 2. If
 * container doesn't exist → pull postgres:16 + create it 3. If container exists
 * but stopped → start it 4. Poll port 5499 until Postgres is ready (max 60 s)
 */
@Slf4j
public class SystemDbProvisioner implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String RUNTIME_CONTAINER_ID_PROPERTY = "dbdeployer.system-db.runtime.container-id";
  public static final String RUNTIME_CONTAINER_NAME_PROPERTY = "dbdeployer.system-db.runtime.container-name";
  public static final String RUNTIME_HOST_PORT_PROPERTY = "dbdeployer.system-db.runtime.host-port";

  @Override
  public void initialize(
    ConfigurableApplicationContext ctx) {
    ConfigurableEnvironment env = ctx.getEnvironment();

    // When the system DB is provided externally (e.g. docker-compose), skip
    // provisioning.
    boolean autoProvision = Boolean.parseBoolean(env.getProperty("dbdeployer.system-db.auto-provision", "true"));
    if (!autoProvision) {
      log.info("SystemDbProvisioner — auto-provision disabled; skipping (external DB expected)");
      return;
    }

    String containerName = env.getProperty("dbdeployer.system-db.container-name", "dbdeployer-system-db");
    String image = env.getProperty("dbdeployer.system-db.image", "postgres:16");
    int hostPort = Integer.parseInt(env.getProperty("dbdeployer.system-db.host-port", "5499"));
    String username = env.getProperty("dbdeployer.system-db.username", "dbdeployer");
    String password = env.getProperty("dbdeployer.system-db.password", "dbdeployer_internal");
    String database = env.getProperty("dbdeployer.system-db.database", "dbdeployer");
    String dataDir = env.getProperty("dbdeployer.system-db.data-dir",
        System.getProperty("user.home") + "/.db-deployer/system-db");

    log.info("SystemDbProvisioner — ensuring system Postgres is running on port {}", hostPort);

    DockerClient docker = buildDockerClient();

    try {
      docker.pingCmd().exec();
    } catch (Exception e) {
      String dockerHost = System.getProperty("DOCKER_HOST", "(unset)");
      throw new IllegalStateException(
          "Docker is not available at " + dockerHost + ". Port Wrangler requires Docker to manage "
              + "the system database. Please ensure Docker Desktop is running and try again.",
          e);
    }

    SystemContainerState containerState = ensureContainerRunning(docker,
        containerName,
        image,
        hostPort,
        username,
        password,
        database,
        dataDir);

    if (containerState.containerId() != null && !containerState.containerId().isBlank()) {
      System.setProperty(RUNTIME_CONTAINER_ID_PROPERTY, containerState.containerId());
    }
    System.setProperty(RUNTIME_CONTAINER_NAME_PROPERTY, containerName);
    System.setProperty(RUNTIME_HOST_PORT_PROPERTY, String.valueOf(hostPort));

    waitForPostgres(hostPort, 60);

    log.info("System Postgres is ready at localhost:{}", hostPort);
  }

  // ── Container lifecycle ────────────────────────────────────────────────────

  private SystemContainerState ensureContainerRunning(
    DockerClient docker,
    String containerName,
    String image,
    int hostPort,
    String username,
    String password,
    String database,
    String dataDir) {
    try {
      var info = docker.inspectContainerCmd(containerName).exec();
      Boolean running = info.getState().getRunning();

      if (Boolean.TRUE.equals(running)) {
        log.info("System DB container '{}' is already running", containerName);
        return new SystemContainerState(info.getId());
      }

      log.info("System DB container '{}' exists but is stopped — starting it", containerName);
      docker.startContainerCmd(containerName).exec();
      return new SystemContainerState(docker.inspectContainerCmd(containerName).exec().getId());

    } catch (NotFoundException e) {
      // Container doesn't exist at all — create it from scratch
      createAndStartContainer(docker, containerName, image, hostPort, username, password, database, dataDir);
      return new SystemContainerState(docker.inspectContainerCmd(containerName).exec().getId());
    }
  }

  private void createAndStartContainer(
    DockerClient docker,
    String containerName,
    String image,
    int hostPort,
    String username,
    String password,
    String database,
    String dataDir) {
    // Ensure the host data directory exists before bind-mounting it
    Path dataDirPath = Paths.get(dataDir).toAbsolutePath();
    try {
      Files.createDirectories(dataDirPath);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot create system DB data directory: " + dataDirPath, e);
    }

    log.info("Pulling system DB image: {}", image);
    try {
      docker.pullImageCmd(image).start().awaitCompletion();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while pulling system DB image", e);
    }

    log.info("Creating system DB container: {} (data dir: {})", containerName, dataDirPath);

    ExposedPort containerPort = ExposedPort.tcp(5432);
    Ports portBindings = new Ports();
    portBindings.bind(containerPort, Ports.Binding.bindPort(hostPort));

    Volume pgDataVolume = new Volume("/var/lib/postgresql/data");

    docker.createContainerCmd(image).withName(containerName)
        .withEnv("POSTGRES_USER=" + username,
            "POSTGRES_PASSWORD=" + password,
            "POSTGRES_DB=" + database,
            "TZ=UTC",
            "PGTZ=UTC")
        .withExposedPorts(containerPort).withVolumes(pgDataVolume)
        .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings)
            .withRestartPolicy(RestartPolicy.unlessStoppedRestart())
            .withBinds(new Bind(dataDirPath.toString(), pgDataVolume)))
        .exec();

    docker.startContainerCmd(containerName).exec();
    log.info("System DB container started: {}", containerName);
  }

  // ── Readiness probe ───────────────────────────────────────────────────────

  private void waitForPostgres(
    int port,
    int maxSeconds) {
    log.info("Waiting for system Postgres to accept connections on port {}...", port);
    int safeMaxSeconds = Math.max(1, maxSeconds);

    for (int elapsedSeconds = 0; elapsedSeconds < safeMaxSeconds; elapsedSeconds++) {
      if (isPortOpen("localhost", port)) {
        log.info("Postgres startup progress {}", formatProgressBar(elapsedSeconds, safeMaxSeconds, true));
        return;
      }

      log.info("Postgres startup progress {}", formatProgressBar(elapsedSeconds + 1, safeMaxSeconds, false));

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for system Postgres");
      }
    }

    throw new IllegalStateException("System Postgres did not become ready within " + maxSeconds + " seconds on port "
        + port + ". Check Docker logs for container 'dbdeployer-system-db'.");
  }

  private String formatProgressBar(
    int elapsedSeconds,
    int maxSeconds,
    boolean ready) {
    int safeMaxSeconds = Math.max(1, maxSeconds);
    int clampedElapsed = Math.max(0, Math.min(elapsedSeconds, safeMaxSeconds));
    int barWidth = 24;
    int filled = (int) Math.round((clampedElapsed * barWidth) / (double) safeMaxSeconds);
    int percent = (int) Math.round((clampedElapsed * 100.0) / safeMaxSeconds);

    String bar = "[" + "=".repeat(filled) + "-".repeat(barWidth - filled) + "]";
    String status = ready ? "ready" : "waiting";

    return bar + " " + percent + "% (" + clampedElapsed + "s/" + safeMaxSeconds + "s, " + status + ")";
  }

  private boolean isPortOpen(
    String host,
    int port) {
    try (Socket ignored = new Socket(host, port)) {
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  // ── Docker client factory ─────────────────────────────────────────────────

  private DockerClient buildDockerClient() {
    String socketUri = DockerSocketResolver.resolve();
    var config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(socketUri).build();
    var httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost())
        .sslConfig(config.getSSLConfig()).build();
    return DockerClientImpl.getInstance(config, httpClient);
  }

  private record SystemContainerState(String containerId) {
  }
}
