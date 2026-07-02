package com.dbdeployer.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Thin client over a running Ollama's admin HTTP API — the runtime dashboard's hands. Uses the
 * JDK's built-in {@link HttpClient} like {@link OllamaModelPuller} (no new dependency).
 *
 * <p>Model lifecycle in Ollama is <b>memory residency, not containers</b>: one Ollama container
 * serves every pulled model. "Run" loads a model into RAM/VRAM ({@code keep_alive} &gt; 0 or -1);
 * "pause" unloads it ({@code keep_alive: 0}); {@code GET /api/ps} reports what is resident.
 */
@Slf4j
@Component
public class OllamaAdminClient {

  /** A model present on the runtime's disk ({@code GET /api/tags}). */
  public record LocalModel(
      String name, long sizeBytes, String digest, String quantization, String parameterSize) {}

  /** A model resident in memory right now ({@code GET /api/ps}). */
  public record LoadedModel(String name, long sizeBytes, long sizeVramBytes, String expiresAt) {}

  /** Outcome of a state-changing admin call. */
  public record AdminResult(boolean success, String message) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  /** Models on disk. Throws on an unreachable runtime — callers decide how to degrade. */
  public List<LocalModel> listLocal(String baseUrl) throws Exception {
    return parseTags(get(baseUrl, "/api/tags", Duration.ofSeconds(20)));
  }

  /** Models resident in memory. Throws on an unreachable runtime. */
  public List<LoadedModel> listLoaded(String baseUrl) throws Exception {
    return parsePs(get(baseUrl, "/api/ps", Duration.ofSeconds(20)));
  }

  /**
   * Loads a model into memory by issuing an empty generate request. {@code keepAlive} governs
   * residency: a duration ("5m"), "-1" (resident until unloaded), or "0". Loading a large model
   * cold can take minutes, hence the generous timeout.
   */
  public AdminResult load(String baseUrl, String model, String keepAlive) {
    return postGenerate(baseUrl, model, keepAlive, Duration.ofMinutes(5), "load");
  }

  /** Unloads a model from memory immediately ({@code keep_alive: 0}). The blobs stay on disk. */
  public AdminResult unload(String baseUrl, String model) {
    return postGenerate(baseUrl, model, "0", Duration.ofSeconds(30), "unload");
  }

  /** Deletes a model's blobs from the runtime's disk ({@code DELETE /api/delete}). */
  public AdminResult delete(String baseUrl, String model) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(root(baseUrl) + "/api/delete"))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(30))
              .method("DELETE", HttpRequest.BodyPublishers.ofString(nameBody(model)))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 == 2) {
        return new AdminResult(true, "Deleted model: " + model);
      }
      return new AdminResult(
          false, "Delete failed (HTTP " + response.statusCode() + "): " + response.body());
    } catch (Exception e) {
      return new AdminResult(false, "Delete failed for " + model + ": " + e.getMessage());
    }
  }

  private AdminResult postGenerate(
      String baseUrl, String model, String keepAlive, Duration timeout, String action) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(root(baseUrl) + "/api/generate"))
              .header("Content-Type", "application/json")
              .timeout(timeout)
              .POST(HttpRequest.BodyPublishers.ofString(keepAliveBody(model, keepAlive)))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 == 2) {
        return new AdminResult(true, action + " ok: " + model);
      }
      return new AdminResult(
          false, action + " failed (HTTP " + response.statusCode() + "): " + response.body());
    } catch (Exception e) {
      return new AdminResult(false, action + " failed for " + model + ": " + e.getMessage());
    }
  }

  private String get(String baseUrl, String path, Duration timeout) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(root(baseUrl) + path)).timeout(timeout).GET().build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException(path + " returned HTTP " + response.statusCode());
    }
    return response.body();
  }

  private static String root(String baseUrl) {
    return baseUrl.replaceAll("/+$", "");
  }

  // ── Pure, unit-testable JSON helpers ────────────────────────────────────────

  /** {@code {"model":"m","stream":false,"keep_alive":<raw int or quoted duration>}} */
  static String keepAliveBody(String model, String keepAlive) {
    String ka = keepAlive == null || keepAlive.isBlank() ? "-1" : keepAlive.trim();
    String kaJson = ka.matches("-?\\d+") ? ka : "\"" + escape(ka) + "\"";
    return "{\"model\":\"" + escape(model) + "\",\"stream\":false,\"keep_alive\":" + kaJson + "}";
  }

  static String nameBody(String model) {
    return "{\"name\":\"" + escape(model) + "\"}";
  }

  static List<LocalModel> parseTags(String json) throws Exception {
    List<LocalModel> models = new ArrayList<>();
    JsonNode root = MAPPER.readTree(json);
    for (JsonNode m : root.path("models")) {
      JsonNode details = m.path("details");
      models.add(
          new LocalModel(
              m.path("name").asText(),
              m.path("size").asLong(0),
              m.path("digest").asText(""),
              details.path("quantization_level").asText(""),
              details.path("parameter_size").asText("")));
    }
    return models;
  }

  static List<LoadedModel> parsePs(String json) throws Exception {
    List<LoadedModel> models = new ArrayList<>();
    JsonNode root = MAPPER.readTree(json);
    for (JsonNode m : root.path("models")) {
      models.add(
          new LoadedModel(
              m.path("name").asText(),
              m.path("size").asLong(0),
              m.path("size_vram").asLong(0),
              m.path("expires_at").asText("")));
    }
    return models;
  }

  private static String escape(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
