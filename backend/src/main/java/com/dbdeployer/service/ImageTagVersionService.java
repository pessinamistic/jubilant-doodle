package com.dbdeployer.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.dbdeployer.config.ImageValidationProperties;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.DbType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Resolves deployable image tags from the image's registry so deploy versions
 * are sourced dynamically rather than from static catalog lists only.
 */
@Service
public class ImageTagVersionService {

    private static final int MAX_TAGS = 120;
    private static final int MAX_PAGES = 4;
    private static final long CACHE_TTL_MS = 15 * 60 * 1000;

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final ImageValidationProperties props;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ImageTagVersionService(ImageValidationProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(props.getDockerHubTimeoutMs(), 1000)))
                .build();
    }

    public List<String> resolveVersions(DbType dbType, boolean refresh) {
        DatabaseCatalog.DbDefinition def = DatabaseCatalog.get(dbType);
        if (def == null) {
            throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
        return resolveVersions(def, refresh);
    }

    private List<String> resolveVersions(DatabaseCatalog.DbDefinition def, boolean refresh) {
        List<String> fallback = sanitize(def.versions());
        if (def.dockerImage() == null || def.dockerImage().isBlank()) {
            return fallback;
        }

        String imageKey = def.dockerImage().trim().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        if (!refresh) {
            CacheEntry entry = cache.get(imageKey);
            if (entry != null && entry.expiresAtMs() > now) {
                return entry.tags();
            }
        }

        List<String> discovered = discoverTags(def.dockerImage());
        List<String> merged = merge(discovered, fallback);

        cache.put(imageKey, new CacheEntry(merged, now + CACHE_TTL_MS));
        return merged;
    }

    private List<String> discoverTags(String image) {
        RegistryRef ref = parseImage(image);
        if (ref == null || ref.repository().isBlank()) {
            return List.of();
        }

        try {
            return switch (ref.type()) {
                case DOCKER_HUB -> fetchDockerHubTags(ref.repository());
                case QUAY -> fetchQuayTags(ref.repository());
                case OCI -> fetchOciTags(ref.host(), ref.repository());
            };
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private List<String> fetchDockerHubTags(String repository) throws IOException, InterruptedException {
        String namespace;
        String repo;

        int slash = repository.indexOf('/');
        if (slash < 0) {
            namespace = "library";
            repo = repository;
        } else {
            namespace = repository.substring(0, slash);
            repo = repository.substring(slash + 1);
        }

        List<String> tags = new ArrayList<>();
        int pageSize = Math.min(100, MAX_TAGS);

        for (int page = 1; page <= MAX_PAGES && tags.size() < MAX_TAGS; page++) {
            String url = "https://hub.docker.com/v2/namespaces/"
                    + urlEncode(namespace)
                    + "/repositories/"
                    + encodePath(repo)
                    + "/tags?page_size=" + pageSize + "&page=" + page;

            HttpResponse<String> response = send(url);
            if (response.statusCode() != 200) {
                break;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (results.isArray()) {
                for (JsonNode result : results) {
                    addTag(tags, result.path("name").asText(null));
                    if (tags.size() >= MAX_TAGS) {
                        break;
                    }
                }
            }

            JsonNode next = root.get("next");
            if (next == null || next.isNull() || next.asText("").isBlank()) {
                break;
            }
        }

        return sanitize(tags);
    }

    private List<String> fetchQuayTags(String repository) throws IOException, InterruptedException {
        List<String> tags = new ArrayList<>();
        int pageSize = Math.min(100, MAX_TAGS);

        for (int page = 1; page <= MAX_PAGES && tags.size() < MAX_TAGS; page++) {
            String url = "https://quay.io/api/v1/repository/"
                    + encodePath(repository)
                    + "/tag/?onlyActiveTags=true&limit=" + pageSize + "&page=" + page;

            HttpResponse<String> response = send(url);
            if (response.statusCode() != 200) {
                break;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode jsonTags = root.path("tags");
            if (jsonTags.isArray()) {
                for (JsonNode tag : jsonTags) {
                    addTag(tags, tag.path("name").asText(null));
                    if (tags.size() >= MAX_TAGS) {
                        break;
                    }
                }
            }

            if (!root.path("has_additional").asBoolean(false)) {
                break;
            }
        }

        return sanitize(tags);
    }

    private List<String> fetchOciTags(String host, String repository) throws IOException, InterruptedException {
        String url = "https://" + host + "/v2/" + encodePath(repository) + "/tags/list?n=" + MAX_TAGS;
        HttpResponse<String> response = send(url);

        if (response.statusCode() != 200) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode tags = root.path("tags");
        if (!tags.isArray()) {
            return List.of();
        }

        List<String> collected = new ArrayList<>();
        for (JsonNode tag : tags) {
            addTag(collected, tag.asText(null));
            if (collected.size() >= MAX_TAGS) {
                break;
            }
        }

        return sanitize(collected);
    }

    private HttpResponse<String> send(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(props.getDockerHubTimeoutMs(), 1000)))
                .header("Accept", "application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private RegistryRef parseImage(String image) {
        if (image == null || image.isBlank()) {
            return null;
        }

        String normalized = image.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith("docker.io/")) {
            normalized = normalized.substring("docker.io/".length());
        } else if (normalized.startsWith("index.docker.io/")) {
            normalized = normalized.substring("index.docker.io/".length());
        }

        String[] parts = normalized.split("/");
        if (parts.length == 0) {
            return null;
        }

        if (isRegistryHost(parts[0])) {
            String host = parts[0];
            String repository = stripTag(String.join("/", Arrays.copyOfRange(parts, 1, parts.length)));
            if (repository.isBlank()) {
                return null;
            }
            RegistryType type = "quay.io".equals(host) ? RegistryType.QUAY : RegistryType.OCI;
            if ("docker.io".equals(host) || "index.docker.io".equals(host) || "hub.docker.com".equals(host)) {
                type = RegistryType.DOCKER_HUB;
            }
            return new RegistryRef(type, host, repository);
        }

        String repository = parts.length == 1
                ? "library/" + stripTag(parts[0])
                : stripTag(String.join("/", parts));
        return new RegistryRef(RegistryType.DOCKER_HUB, "docker.io", repository);
    }

    private boolean isRegistryHost(String firstSegment) {
        return firstSegment.contains(".") || firstSegment.contains(":") || "localhost".equals(firstSegment);
    }

    private String stripTag(String repository) {
        String result = repository;

        int digest = result.indexOf('@');
        if (digest > 0) {
            result = result.substring(0, digest);
        }

        int slash = result.lastIndexOf('/');
        int colon = result.lastIndexOf(':');
        if (colon > slash) {
            result = result.substring(0, colon);
        }

        return result;
    }

    private List<String> merge(List<String> discovered, List<String> fallback) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(sanitize(discovered));
        merged.addAll(sanitize(fallback));

        if (merged.isEmpty()) {
            merged.add("latest");
        }

        return limit(new ArrayList<>(merged));
    }

    private List<String> sanitize(Collection<String> tags) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return limit(new ArrayList<>(unique));
    }

    private List<String> limit(List<String> tags) {
        if (tags.size() <= MAX_TAGS) {
            return List.copyOf(tags);
        }
        return List.copyOf(tags.subList(0, MAX_TAGS));
    }

    private void addTag(List<String> tags, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        tags.add(candidate.trim());
    }

    private String encodePath(String value) {
        return Arrays.stream(value.split("/"))
                .map(this::urlEncode)
                .collect(Collectors.joining("/"));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private enum RegistryType { DOCKER_HUB, QUAY, OCI }

    private record RegistryRef(RegistryType type, String host, String repository) {}

    private record CacheEntry(List<String> tags, long expiresAtMs) {}
}
