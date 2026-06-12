package com.dbdeployer.config;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks whether the Docker daemon is reachable on the current machine by performing a real {@code
 * ping} against the socket resolved by {@link DockerSocketResolver}. This is deliberately separate
 * from {@link com.dbdeployer.deploy.OsDetector#isDockerAvailable()}, which only tests whether the
 * {@code docker} CLI binary is on the PATH — not whether the daemon is actually running.
 */
@Slf4j
@Component
public class DockerHealthChecker {

  /**
   * Result of a Docker daemon reachability probe.
   *
   * @param available {@code true} when the daemon replied to a ping
   * @param dockerHost the socket URI that was probed
   * @param errorMessage non-null only when {@code available} is {@code false}
   */
  public record DockerStatus(boolean available, String dockerHost, String errorMessage) {}

  /**
   * Attempts to ping the Docker daemon. Never throws — all failures are captured in the returned
   * {@link DockerStatus}.
   */
  public DockerStatus check() {
    String socketUri = DockerSocketResolver.resolve();
    log.debug("Probing Docker daemon at {}", socketUri);

    try (var httpClient =
        new ZerodepDockerHttpClient.Builder()
            .dockerHost(URI.create(socketUri))
            .connectionTimeout(Duration.ofSeconds(5))
            .responseTimeout(Duration.ofSeconds(10))
            .build()) {

      var config =
          DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(socketUri).build();

      try (var client = DockerClientImpl.getInstance(config, httpClient)) {
        client.pingCmd().exec();
        log.debug("Docker daemon ping succeeded");
        return new DockerStatus(true, socketUri, null);
      }

    } catch (Exception e) {
      String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
      log.debug("Docker daemon ping failed: {}", msg);
      return new DockerStatus(false, socketUri, msg);
    }
  }
}
