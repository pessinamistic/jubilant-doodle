package com.dbdeployer.service;

import com.dbdeployer.config.ImageValidationProperties;
import com.dbdeployer.model.ImageAvailabilityState;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Lightweight Docker Hub tag checker using the public Hub API. */
@Component
public class DockerHubTagClient {

    public record HubTagResult(ImageAvailabilityState status, String message) {}

    public record DockerHubRepository(String namespace, String repository) {}

    private final HttpClient client;
    private final ImageValidationProperties props;

    public DockerHubTagClient(ImageValidationProperties props) {
        this.props = props;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(props.getDockerHubTimeoutMs(), 1000)))
                .build();
    }

    public HubTagResult checkTag(String image, String tag) {
        DockerHubRepository repo = resolveDockerHubRepository(image);
        if (repo == null) {
            return new HubTagResult(
                    ImageAvailabilityState.NOT_APPLICABLE, "Remote validation skipped for non-Docker Hub image");
        }

        String namespace = urlEncode(repo.namespace());
        String repository = urlEncode(repo.repository());
        String safeTag = urlEncode(tag);
        String url = "https://hub.docker.com/v2/namespaces/" + namespace + "/repositories/" + repository + "/tags/"
                + safeTag;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(Duration.ofMillis(Math.max(props.getDockerHubTimeoutMs(), 1000)))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return new HubTagResult(ImageAvailabilityState.AVAILABLE, "Tag exists on Docker Hub");
            }
            if (status == 404) {
                return new HubTagResult(ImageAvailabilityState.MISSING, "Tag not found on Docker Hub");
            }
            if (status == 429) {
                return new HubTagResult(ImageAvailabilityState.UNKNOWN, "Docker Hub rate limit reached");
            }
            return new HubTagResult(ImageAvailabilityState.UNKNOWN, "Docker Hub check returned HTTP " + status);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new HubTagResult(ImageAvailabilityState.UNKNOWN, "Docker Hub check failed: " + e.getMessage());
        }
    }

    /**
     * Converts a Docker image name into Docker Hub namespace/repository if
     * applicable. Returns null for non-Docker Hub images.
     */
    public DockerHubRepository resolveDockerHubRepository(String image) {
        if (image == null || image.isBlank()) return null;

        String normalized = image.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith("docker.io/")) {
            normalized = normalized.substring("docker.io/".length());
        } else if (normalized.startsWith("index.docker.io/")) {
            normalized = normalized.substring("index.docker.io/".length());
        }

        String[] parts = normalized.split("/");
        if (parts.length > 0 && isRegistryHost(parts[0])) {
            return null;
        }

        if (parts.length == 1) {
            return new DockerHubRepository("library", parts[0]);
        }

        return new DockerHubRepository(
                parts[0], String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length)));
    }

    private boolean isRegistryHost(String firstSegment) {
        return firstSegment.contains(".") || firstSegment.contains(":") || "localhost".equals(firstSegment);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
