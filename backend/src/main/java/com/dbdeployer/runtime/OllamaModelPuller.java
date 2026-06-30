package com.dbdeployer.runtime;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pulls a model into a running Ollama runtime via its HTTP API ({@code POST /api/pull}). Uses the
 * JDK's built-in {@link HttpClient} — no new dependency. The request-body construction is a pure,
 * unit-testable static method.
 */
@Slf4j
@Component
public class OllamaModelPuller {

  /** Outcome of a pull attempt. */
  public record PullResult(boolean success, String message) {}

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /**
   * Pulls {@code modelTag} into the Ollama runtime at {@code baseUrl} (blocking). Uses {@code
   * stream:false} so the call returns once the pull completes.
   */
  public PullResult pull(String baseUrl, String modelTag) {
    String url = baseUrl.replaceAll("/+$", "") + "/api/pull";
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofMinutes(30))
              .POST(HttpRequest.BodyPublishers.ofString(buildPullBody(modelTag)))
              .build();

      log.info("[model] Pulling '{}' into runtime {}", modelTag, baseUrl);
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() / 100 == 2) {
        log.info("[model] Pull complete for '{}'", modelTag);
        return new PullResult(true, "Pulled model: " + modelTag);
      }
      return new PullResult(
          false, "Ollama pull failed (HTTP " + response.statusCode() + "): " + response.body());
    } catch (Exception e) {
      log.warn("[model] Pull failed for '{}': {}", modelTag, e.getMessage());
      return new PullResult(false, "Pull failed for " + modelTag + ": " + e.getMessage());
    }
  }

  /** Pure JSON body builder — {@code {"name":"<tag>","stream":false}} with escaping. */
  static String buildPullBody(String modelTag) {
    String escaped = modelTag == null ? "" : modelTag.replace("\\", "\\\\").replace("\"", "\\\"");
    return "{\"name\":\"" + escaped + "\",\"stream\":false}";
  }
}
