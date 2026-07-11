package com.dbdeployer.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fallback discovery for models that aren't in the curated {@link ModelCatalog}: proxies the
 * community-run <a href="https://ollamadb.dev">ollamadb.dev</a> API, which mirrors
 * ollama.com/library with search/sort support that the official library page doesn't expose.
 *
 * <p>Not affiliated with Ollama — this is a best-effort convenience layer, not part of the
 * hardware-scored Cookbook path. Any failure (timeout, network, bad response) degrades to an
 * empty result rather than surfacing an error, since the curated catalog + free-text pull already
 * cover the primary flow.
 */
@Slf4j
@Service
public class OllamaLibrarySearchService {

  private static final String BASE_URL = "https://ollamadb.dev/api/v1/models";
  private static final int MAX_RESULTS = 20;
  private static final long CACHE_TTL_MS = 10 * 60 * 1000;
  private static final Duration TIMEOUT = Duration.ofMillis(3000);

  private final HttpClient client;
  private final ObjectMapper objectMapper;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public OllamaLibrarySearchService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  }

  /** Searches the remote library by name/description, ranked by pull count. Fails soft to []. */
  public List<OllamaLibraryModel> search(String query, int limit) {
    String q = query == null ? "" : query.trim();
    if (q.isBlank()) {
      return List.of();
    }
    int cappedLimit = Math.min(Math.max(limit, 1), MAX_RESULTS);
    String cacheKey = q.toLowerCase(Locale.ROOT) + "|" + cappedLimit;

    long now = System.currentTimeMillis();
    CacheEntry cached = cache.get(cacheKey);
    if (cached != null && cached.expiresAtMs() > now) {
      return cached.results();
    }

    List<OllamaLibraryModel> results = fetch(q, cappedLimit);
    cache.put(cacheKey, new CacheEntry(results, now + CACHE_TTL_MS));
    return results;
  }

  private List<OllamaLibraryModel> fetch(String query, int limit) {
    String url =
        BASE_URL
            + "?search="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&limit="
            + limit
            + "&sort_by=pulls&order=desc";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.warn("[ollama-library] search '{}' returned HTTP {}", query, response.statusCode());
        return List.of();
      }
      return parse(response.body());
    } catch (IOException e) {
      log.warn("[ollama-library] search '{}' failed: {}", query, e.getMessage());
      return List.of();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return List.of();
    } catch (RuntimeException e) {
      log.warn("[ollama-library] search '{}' returned unparseable response: {}", query, e.getMessage());
      return List.of();
    }
  }

  private List<OllamaLibraryModel> parse(String body) throws IOException {
    JsonNode root = objectMapper.readTree(body);
    JsonNode models = root.path("models");
    if (!models.isArray()) {
      return List.of();
    }

    List<OllamaLibraryModel> out = new ArrayList<>();
    for (JsonNode m : models) {
      String identifier = m.path("model_identifier").asText(null);
      if (identifier == null || identifier.isBlank()) {
        continue;
      }
      List<String> labels = new ArrayList<>();
      JsonNode labelsNode = m.path("labels");
      if (labelsNode.isArray()) {
        for (JsonNode l : labelsNode) {
          labels.add(l.asText());
        }
      }
      out.add(
          new OllamaLibraryModel(
              identifier,
              m.path("namespace").isNull() ? null : m.path("namespace").asText(null),
              m.path("description").asText(""),
              m.path("capability").isNull() ? null : m.path("capability").asText(null),
              labels,
              m.path("pulls").asLong(0),
              m.path("tags").asInt(0),
              "official".equalsIgnoreCase(m.path("model_type").asText("official")),
              m.path("url").asText(null)));
    }
    return out;
  }

  private record CacheEntry(List<OllamaLibraryModel> results, long expiresAtMs) {}
}
