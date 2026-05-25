package com.dbdeployer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the correct Docker socket URI for the current machine and sets
 * it as the DOCKER_HOST system property so all docker-java transports
 * (including zerodep) pick it up automatically.
 *
 * Priority order:
 *  1. DOCKER_HOST environment variable (user override)
 *  2. Colima default socket  (~/.colima/default/docker.sock)
 *  3. Colima named profile   (~/.colima/<profile>/docker.sock)
 *  4. Standard Unix socket   (/var/run/docker.sock)
 *  5. Rootless Docker socket (~/.docker/run/docker.sock)
 *  6. Docker Desktop socket  (~/.docker/desktop/run/docker.sock)  [macOS]
 */
public class DockerSocketResolver {

    private static final Logger log = LoggerFactory.getLogger(DockerSocketResolver.class);

    /**
     * Resolves the Docker socket URI and also exports it as the
     * {@code DOCKER_HOST} system property so zerodep transport picks it up.
     */
    public static String resolve() {
        String uri = detect();
        // Zerodep transport reads the DOCKER_HOST system property at client-build time
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

        String home = System.getProperty("user.home");

        // 2. Colima default profile
        Path colimaDefault = Paths.get(home, ".colima", "default", "docker.sock");
        if (Files.exists(colimaDefault)) {
            String uri = "unix://" + colimaDefault.toAbsolutePath();
            log.info("Docker socket resolved to Colima default: {}", uri);
            return uri;
        }

        // 3. Colima — any named profile (pick first found)
        Path colimaDir = Paths.get(home, ".colima");
        if (Files.isDirectory(colimaDir)) {
            try {
                var found = Files.list(colimaDir)
                        .filter(Files::isDirectory)
                        .map(p -> p.resolve("docker.sock"))
                        .filter(Files::exists)
                        .findFirst();
                if (found.isPresent()) {
                    String uri = "unix://" + found.get().toAbsolutePath();
                    log.info("Docker socket resolved to Colima profile: {}", uri);
                    return uri;
                }
            } catch (Exception ignored) {}
        }

        // 4. Standard Unix socket (Linux / Docker Engine)
        if (Files.exists(Path.of("/var/run/docker.sock"))) {
            log.info("Docker socket resolved to standard Unix socket");
            return "unix:///var/run/docker.sock";
        }

        // 5. Rootless Docker
        Path rootless = Paths.get(home, ".docker", "run", "docker.sock");
        if (Files.exists(rootless)) {
            String uri = "unix://" + rootless.toAbsolutePath();
            log.info("Docker socket resolved to rootless: {}", uri);
            return uri;
        }

        // 6. Docker Desktop (macOS)
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
