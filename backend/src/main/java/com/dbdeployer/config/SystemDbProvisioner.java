package com.dbdeployer.config;

import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

/**
 * Runs before any Spring bean is created.
 *
 * Ensures the dedicated system Postgres container ("dbdeployer-system-db") is
 * up and accepting connections before HikariCP tries to connect to it.
 *
 * Flow:
 *   1. Connect to Docker daemon
 *   2. If container doesn't exist → pull postgres:16 + create it
 *   3. If container exists but stopped → start it
 *   4. Poll port 5499 until Postgres is ready (max 60 s)
 */
public class SystemDbProvisioner
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(SystemDbProvisioner.class);

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        ConfigurableEnvironment env = ctx.getEnvironment();

        // When the system DB is provided externally (e.g. docker-compose), skip provisioning.
        boolean autoProvision = Boolean.parseBoolean(
                env.getProperty("dbdeployer.system-db.auto-provision", "true"));
        if (!autoProvision) {
            log.info("SystemDbProvisioner — auto-provision disabled; skipping (external DB expected)");
            return;
        }

        String containerName = env.getProperty("dbdeployer.system-db.container-name", "dbdeployer-system-db");
        String image         = env.getProperty("dbdeployer.system-db.image",          "postgres:16");
        int    hostPort      = Integer.parseInt(env.getProperty("dbdeployer.system-db.host-port", "5499"));
        String username      = env.getProperty("dbdeployer.system-db.username",       "dbdeployer");
        String password      = env.getProperty("dbdeployer.system-db.password",       "dbdeployer_internal");
        String database      = env.getProperty("dbdeployer.system-db.database",       "dbdeployer");

        log.info("SystemDbProvisioner — ensuring system Postgres is running on port {}", hostPort);

        DockerClient docker = buildDockerClient();

        try {
            docker.pingCmd().exec();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Docker is not available. Port Wrangler requires Docker to manage the system database. " +
                "Please start Docker and try again.", e);
        }

        ensureContainerRunning(docker, containerName, image, hostPort, username, password, database);
        waitForPostgres(hostPort, 60);

        log.info("System Postgres is ready at localhost:{}", hostPort);
    }

    // ── Container lifecycle ────────────────────────────────────────────────────

    private void ensureContainerRunning(DockerClient docker, String containerName,
                                        String image, int hostPort,
                                        String username, String password, String database) {
        try {
            var info = docker.inspectContainerCmd(containerName).exec();
            Boolean running = info.getState().getRunning();

            if (Boolean.TRUE.equals(running)) {
                log.info("System DB container '{}' is already running", containerName);
                return;
            }

            log.info("System DB container '{}' exists but is stopped — starting it", containerName);
            docker.startContainerCmd(containerName).exec();

        } catch (NotFoundException e) {
            // Container doesn't exist at all — create it from scratch
            createAndStartContainer(docker, containerName, image, hostPort, username, password, database);
        }
    }

    private void createAndStartContainer(DockerClient docker, String containerName,
                                         String image, int hostPort,
                                         String username, String password, String database) {
        log.info("Pulling system DB image: {}", image);
        try {
            docker.pullImageCmd(image)
                    .start()
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pulling system DB image", e);
        }

        log.info("Creating system DB container: {}", containerName);

        ExposedPort containerPort = ExposedPort.tcp(5432);
        Ports portBindings = new Ports();
        portBindings.bind(containerPort, Ports.Binding.bindPort(hostPort));

        docker.createContainerCmd(image)
                .withName(containerName)
                .withEnv(
                        "POSTGRES_USER="     + username,
                        "POSTGRES_PASSWORD=" + password,
                        "POSTGRES_DB="       + database
                )
                .withExposedPorts(containerPort)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withRestartPolicy(RestartPolicy.unlessStoppedRestart()))
                .exec();

        docker.startContainerCmd(containerName).exec();
        log.info("System DB container started: {}", containerName);
    }

    // ── Readiness probe ───────────────────────────────────────────────────────

    private void waitForPostgres(int port, int maxSeconds) {
        log.info("Waiting for system Postgres to accept connections on port {}...", port);
        long deadline = System.currentTimeMillis() + (maxSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            if (isPortOpen("localhost", port)) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for system Postgres");
            }
        }

        throw new IllegalStateException(
            "System Postgres did not become ready within " + maxSeconds + " seconds on port " + port +
            ". Check Docker logs for container 'dbdeployer-system-db'.");
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Docker client factory ─────────────────────────────────────────────────

    private DockerClient buildDockerClient() {
        String socketUri = DockerSocketResolver.resolve();
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(socketUri)
                .build();
        var httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
