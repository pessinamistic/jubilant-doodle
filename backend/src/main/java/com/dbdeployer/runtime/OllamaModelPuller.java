package com.dbdeployer.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pulls a model into a running Ollama runtime via its HTTP API ({@code POST /api/pull}). Uses the
 * JDK's built-in {@link HttpClient} — no new dependency. Streams the NDJSON progress lines Ollama
 * emits ({@code stream:true}) and reports layer download progress through a callback so the UI can
 * render a live progress bar. The body construction and line parsing are pure, unit-testable static
 * methods.
 */
@Slf4j
@Component
public class OllamaModelPuller {

  /** Outcome of a pull attempt. */
  public record PullResult(boolean success, String message) {}

  /** A single progress observation from the pull stream. */
  public record PullProgress(String status, long totalBytes, long completedBytes) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  /** Pulls without progress reporting (kept for callers that don't need it). */
  public PullResult pull(String baseUrl, String modelTag) {
    return pull(baseUrl, modelTag, p -> {});
  }

  /**
   * Pulls {@code modelTag} into the Ollama runtime at {@code baseUrl} (blocking). Uses {@code
   * stream:true} and invokes {@code onProgress} for every progress line so callers can surface live
   * download state; returns once the stream ends.
   */
  public PullResult pull(String baseUrl, String modelTag, Consumer<PullProgress> onProgress) {
    String url = baseUrl.replaceAll("/+$", "") + "/api/pull";
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofMinutes(30))
              .POST(HttpRequest.BodyPublishers.ofString(buildPullBody(modelTag)))
              .build();

      log.info("[model] Pulling '{}' into runtime {}", modelTag, baseUrl);
      HttpResponse<java.io.InputStream> response =
          http.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() / 100 != 2) {
        String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
        return new PullResult(
            false, "Ollama pull failed (HTTP " + response.statusCode() + "): " + body);
      }

      String error = null;
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isBlank()) continue;
          String lineError = extractError(line);
          if (lineError != null) {
            error = lineError;
            continue;
          }
          PullProgress progress = parseProgressLine(line);
          if (progress != null) onProgress.accept(progress);
        }
      }

      if (error != null) {
        log.warn("[model] Pull failed for '{}': {}", modelTag, error);
        return new PullResult(false, "Pull failed for " + modelTag + ": " + error);
      }
      log.info("[model] Pull complete for '{}'", modelTag);
      return new PullResult(true, "Pulled model: " + modelTag);
    } catch (Exception e) {
      log.warn("[model] Pull failed for '{}': {}", modelTag, e.getMessage());
      return new PullResult(false, "Pull failed for " + modelTag + ": " + e.getMessage());
    }
  }

  /** Pure JSON body builder — {@code {"name":"<tag>","stream":true}} with escaping. */
  static String buildPullBody(String modelTag) {
    String escaped = modelTag == null ? "" : modelTag.replace("\\", "\\\\").replace("\"", "\\\"");
    return "{\"name\":\"" + escaped + "\",\"stream\":true}";
  }

  /**
   * Parses one NDJSON progress line into a {@link PullProgress}, or {@code null} when the line is
   * unparseable. Ollama emits {@code {"status":"pulling <digest>","total":n,"completed":n}} while
   * downloading and status-only lines ({@code "verifying sha256 digest"}, {@code "success"}) around
   * it.
   */
  static PullProgress parseProgressLine(String line) {
    try {
      JsonNode node = MAPPER.readTree(line);
      String status = node.path("status").asText("");
      long total = node.path("total").asLong(0);
      long completed = node.path("completed").asLong(0);
      return new PullProgress(status, total, completed);
    } catch (Exception e) {
      return null;
    }
  }

  /** Returns the error message when the line carries one, else {@code null}. */
  static String extractError(String line) {
    try {
      JsonNode node = MAPPER.readTree(line);
      return node.hasNonNull("error") ? node.get("error").asText() : null;
    } catch (Exception e) {
      return null;
    }
  }
}
