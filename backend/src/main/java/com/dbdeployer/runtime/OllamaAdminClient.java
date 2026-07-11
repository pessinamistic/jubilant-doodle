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

  /**
   * Full model card from {@code POST /api/show} — capabilities (completion/tools/vision/thinking/
   * embedding), architecture facts from {@code model_info}, and the Modelfile-level text blobs
   * (parameters, template, license).
   */
  public record ModelShow(
      List<String> capabilities,
      String parameters,
      String template,
      String license,
      String modifiedAt,
      String format,
      String family,
      List<String> families,
      String parameterSize,
      String quantizationLevel,
      String architecture,
      Long contextLength,
      Long embeddingLength,
      Long parameterCount) {}

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

  /** Full model card ({@code POST /api/show}). Throws on an unreachable runtime or unknown tag. */
  public ModelShow show(String baseUrl, String model) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(root(baseUrl) + "/api/show"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(modelBody(model)))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException("/api/show returned HTTP " + response.statusCode());
    }
    return parseShow(response.body());
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

  static String modelBody(String model) {
    return "{\"model\":\"" + escape(model) + "\"}";
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

  /** License texts run to tens of KB; cap what we ship to the UI. */
  private static final int MAX_TEXT_BLOB = 8_000;

  static ModelShow parseShow(String json) throws Exception {
    JsonNode root = MAPPER.readTree(json);
    JsonNode details = root.path("details");
    JsonNode info = root.path("model_info");

    List<String> capabilities = new ArrayList<>();
    for (JsonNode c : root.path("capabilities")) {
      capabilities.add(c.asText());
    }
    List<String> families = new ArrayList<>();
    for (JsonNode f : details.path("families")) {
      families.add(f.asText());
    }

    // model_info keys are architecture-prefixed, e.g. "gemma3.context_length".
    String architecture = info.path("general.architecture").asText(null);
    Long contextLength = infoLong(info, architecture, "context_length");
    Long embeddingLength = infoLong(info, architecture, "embedding_length");
    Long parameterCount =
        info.path("general.parameter_count").isNumber()
            ? info.path("general.parameter_count").asLong()
            : null;

    return new ModelShow(
        capabilities,
        truncate(root.path("parameters").asText(null)),
        truncate(root.path("template").asText(null)),
        truncate(root.path("license").asText(null)),
        root.path("modified_at").asText(null),
        details.path("format").asText(null),
        details.path("family").asText(null),
        families,
        details.path("parameter_size").asText(null),
        details.path("quantization_level").asText(null),
        architecture,
        contextLength,
        embeddingLength,
        parameterCount);
  }

  private static Long infoLong(JsonNode info, String architecture, String suffix) {
    if (architecture == null) return null;
    JsonNode node = info.path(architecture + "." + suffix);
    return node.isNumber() ? node.asLong() : null;
  }

  private static String truncate(String s) {
    if (s == null || s.length() <= MAX_TEXT_BLOB) return s;
    return s.substring(0, MAX_TEXT_BLOB) + "\n… (truncated)";
  }

  private static String escape(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
