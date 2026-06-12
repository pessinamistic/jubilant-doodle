package com.dbdeployer.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the correct Docker socket URI for the current machine and sets it as the DOCKER_HOST
 * system property so all docker-java transports (including zerodep) pick it up automatically.
 *
 * <p>Priority order: 1. DOCKER_HOST environment variable (user override) 2. DOCKER_HOST system
 * property (JVM override) 3. Windows named pipe (npipe:////./pipe/docker_engine) 4. Colima default
 * socket (~/.colima/default/docker.sock) 5. Colima named profile (~/.colima/<profile>/docker.sock)
 * 6. Standard Unix socket (/var/run/docker.sock) 7. Rootless Docker socket
 * (~/.docker/run/docker.sock) 8. Docker Desktop socket (~/.docker/desktop/run/docker.sock) [macOS]
 */
@Slf4j
public class DockerSocketResolver {

  /**
   * Resolves the Docker socket URI and also exports it as the {@code DOCKER_HOST} system property
   * so zerodep transport picks it up.
   */
  public static String resolve() {
    String uri = detect();
    // zerodep transport reads the DOCKER_HOST system property at client-build time
    System.setProperty("DOCKER_HOST", uri);
    return uri;
  }

  private static String detect() {
    // 1. Explicit env override
    String envDockerHost = System.getenv("DOCKER_HOST");
    if (envDockerHost != null && !envDockerHost.isBlank()) {
      log.info("Docker socket from DOCKER_HOST env: {}", envDockerHost);
      return envDockerHost;
    }

    // 2. Explicit JVM override
    String systemDockerHost = System.getProperty("DOCKER_HOST");
    if (systemDockerHost != null && !systemDockerHost.isBlank()) {
      log.info("Docker socket from DOCKER_HOST system property: {}", systemDockerHost);
      return systemDockerHost;
    }

    // 3. Windows Docker Desktop default named pipe
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (osName.contains("win")) {
      String uri = "npipe:////./pipe/docker_engine";
      log.info("Docker socket resolved to Windows named pipe: {}", uri);
      return uri;
    }

    String home = System.getProperty("user.home");

    // 4. Colima default profile
    Path colimaDefault = Paths.get(home, ".colima", "default", "docker.sock");
    if (Files.exists(colimaDefault)) {
      String uri = "unix://" + colimaDefault.toAbsolutePath();
      log.info("Docker socket resolved to Colima default: {}", uri);
      return uri;
    }

    // 5. Colima — any named profile (pick first found)
    Path colimaDir = Paths.get(home, ".colima");
    if (Files.isDirectory(colimaDir)) {
      try (var profiles = Files.list(colimaDir)) {
        var found =
            profiles
                .filter(Files::isDirectory)
                .map(p -> p.resolve("docker.sock"))
                .filter(Files::exists)
                .findFirst();
        if (found.isPresent()) {
          String uri = "unix://" + found.get().toAbsolutePath();
          log.info("Docker socket resolved to Colima profile: {}", uri);
          return uri;
        }
      } catch (Exception ignored) {
      }
    }

    // 6. Standard Unix socket (Linux / Docker Engine)
    if (Files.exists(Path.of("/var/run/docker.sock"))) {
      log.info("Docker socket resolved to standard Unix socket");
      return "unix:///var/run/docker.sock";
    }

    // 7. Rootless Docker
    Path rootless = Paths.get(home, ".docker", "run", "docker.sock");
    if (Files.exists(rootless)) {
      String uri = "unix://" + rootless.toAbsolutePath();
      log.info("Docker socket resolved to rootless: {}", uri);
      return uri;
    }

    // 8. Docker Desktop (macOS)
    Path dockerDesktop = Paths.get(home, ".docker", "desktop", "run", "docker.sock");
    if (Files.exists(dockerDesktop)) {
      String uri = "unix://" + dockerDesktop.toAbsolutePath();
      log.info("Docker socket resolved to Docker Desktop: {}", uri);
      return uri;
    }

    // Fallback — let the SDK try its own default
    log.warn("No Docker socket found in known locations — falling back to SDK default");
    return "unix:///var/run/docker.sock";
  }
}
