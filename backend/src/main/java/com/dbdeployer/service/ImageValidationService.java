package com.dbdeployer.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbdeployer.api.dto.ImageCheckResponse;
import com.dbdeployer.api.dto.ImageToolDetailResponse;
import com.dbdeployer.api.dto.ImageToolSummaryResponse;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.ImageAvailabilityState;
import com.dbdeployer.model.ImageTrackingStatus;
import com.dbdeployer.model.ImageValidationDecision;
import com.dbdeployer.store.ImageTrackingStatusRepository;

@Service
public class ImageValidationService {

    private static final Logger log = LoggerFactory.getLogger(ImageValidationService.class);

    public enum RefreshScope {
        LOCAL,
        HUB,
        ALL;

        public static RefreshScope from(String raw) {
            if (raw == null || raw.isBlank()) return ALL;
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "LOCAL" -> LOCAL;
                case "HUB", "DOCKER_HUB", "DOCKERHUB" -> HUB;
                default -> ALL;
            };
        }
    }

    private final DockerDeployEngine docker;
    private final DockerHubTagClient dockerHub;
    private final ImageTagVersionService imageTagVersionService;
    private final ImageTrackingStatusRepository trackingRepo;
    private final ReentrantLock trackingWriteLock = new ReentrantLock(true);

    public ImageValidationService(DockerDeployEngine docker,
                                  DockerHubTagClient dockerHub,
                                  ImageTagVersionService imageTagVersionService,
                                  ImageTrackingStatusRepository trackingRepo) {
        this.docker = docker;
        this.dockerHub = dockerHub;
        this.imageTagVersionService = imageTagVersionService;
        this.trackingRepo = trackingRepo;
    }

    @Transactional
    public ImageCheckResponse checkForDeploy(DbType dbType, String tag) {
        log.info("[image-check] deploy precheck requested: dbType={}, tag={}", dbType, normalizeTag(tag));
        return withTrackingWriteLock(() -> evaluateAndPersist(dbType, tag, true, true));
    }

    @Transactional
    public ImageCheckResponse check(DbType dbType, String tag, boolean refresh) {
        log.debug("[image-check] check requested: dbType={}, tag={}, refresh={}", dbType, normalizeTag(tag), refresh);
        if (!refresh) {
            DatabaseCatalog.DbDefinition def = DatabaseCatalog.get(dbType);
            if (def != null && def.dockerImage() != null) {
                Optional<ImageTrackingStatus> existing = trackingRepo.findByDbTypeAndImageNameAndImageTag(
                        dbType,
                        def.dockerImage(),
                        normalizeTag(tag)
                );
                if (existing.isPresent()) return toResponse(existing.get(), def.displayName());
            }
        }
        return withTrackingWriteLock(() -> evaluateAndPersist(dbType, tag, true, refresh));
    }

    @Transactional
    public ImageCheckResponse refreshLocalOnly(DbType dbType, String tag) {
        log.debug("[image-check] local-only refresh requested: dbType={}, tag={}", dbType, normalizeTag(tag));
        return withTrackingWriteLock(() -> evaluateAndPersist(dbType, tag, false, false));
    }

    @Transactional
    public List<ImageCheckResponse> getOverview() {
        for (DatabaseCatalog.DbDefinition def : deployableCatalog()) {
            ensureTrackedTags(def, false);
        }

        List<ImageTrackingStatus> tracked = trackingRepo.findAllByOrderByDbTypeAscImageNameAscImageTagAsc();
        if (!tracked.isEmpty()) {
            List<ImageCheckResponse> items = new ArrayList<>(tracked.size());
            for (ImageTrackingStatus row : tracked) {
                DatabaseCatalog.DbDefinition def = DatabaseCatalog.get(row.getDbType());
                String display = def != null ? def.displayName() : row.getDbType().name();
                items.add(toResponse(row, display));
            }
            return items;
        }

        // First-time usage fallback: synthesize from catalog without mutating DB.
        List<ImageCheckResponse> fallback = new ArrayList<>();
        for (DatabaseCatalog.DbDefinition def : deployableCatalog()) {
            for (String tag : resolveVersions(def, false)) {
                boolean hubManaged = dockerHub.resolveDockerHubRepository(def.dockerImage()) != null;
                fallback.add(new ImageCheckResponse(
                        def.type(),
                        def.displayName(),
                        def.dockerImage(),
                        tag,
                        def.dockerImage() + ":" + tag,
                        hubManaged,
                        ImageAvailabilityState.UNKNOWN,
                        hubManaged ? ImageAvailabilityState.UNKNOWN : ImageAvailabilityState.NOT_APPLICABLE,
                        ImageValidationDecision.ALLOW_WITH_WARNING,
                        "No checks recorded yet",
                        null,
                        null,
                        null
                ));
            }
        }
        return fallback;
    }

    @Transactional
    public List<ImageToolSummaryResponse> getToolSummaries() {
        List<ImageToolSummaryResponse> summaries = new ArrayList<>();
        for (DatabaseCatalog.DbDefinition def : deployableCatalog()) {
            List<ImageTrackingStatus> rows = ensureTrackedTags(def, false);
            summaries.add(toToolSummary(def, rows));
        }
        return summaries;
    }

    @Transactional
    public ImageToolDetailResponse getToolDetails(DbType dbType, boolean refresh) {
        DatabaseCatalog.DbDefinition def = requireDeployableDefinition(dbType);
        if (refresh) {
            refreshToolStatuses(dbType, RefreshScope.ALL);
        }

        List<ImageTrackingStatus> rows = ensureTrackedTags(def, false);
        List<ImageCheckResponse> tags = new ArrayList<>();

        if (rows.isEmpty()) {
            for (String tag : resolveVersions(def, false)) {
                boolean hubManaged = dockerHub.resolveDockerHubRepository(def.dockerImage()) != null;
                tags.add(new ImageCheckResponse(
                        def.type(),
                        def.displayName(),
                        def.dockerImage(),
                        tag,
                        def.dockerImage() + ":" + tag,
                        hubManaged,
                        ImageAvailabilityState.UNKNOWN,
                        hubManaged ? ImageAvailabilityState.UNKNOWN : ImageAvailabilityState.NOT_APPLICABLE,
                        ImageValidationDecision.ALLOW_WITH_WARNING,
                        "No checks recorded yet",
                        null,
                        null,
                        null
                ));
            }
            return buildToolDetail(def, tags);
        }

        for (ImageTrackingStatus row : rows) {
            tags.add(toResponse(row, def.displayName()));
        }

        return buildToolDetail(def, tags);
    }

    @Transactional
    public int refresh(RefreshScope scope) {
        return switch (scope) {
            case LOCAL -> refreshLocalStatuses();
            case HUB -> refreshDockerHubStatuses();
            case ALL -> refreshAllStatuses();
        };
    }

    @Transactional
    public int refreshAllStatuses() {
        log.info("[image-refresh] Starting full refresh for all deployable tools");
        return withTrackingWriteLock(() -> {
            int updated = 0;
            for (DatabaseCatalog.DbDefinition def : deployableCatalog()) {
                for (String tag : resolveVersions(def, false)) {
                    evaluateAndPersist(def.type(), tag, true, true);
                    updated++;
                }
            }
            log.info("[image-refresh] Completed full refresh: updated {} tags", updated);
            return updated;
        });
    }

    @Transactional
    public int refreshToolStatuses(DbType dbType, RefreshScope scope) {
        log.info("[image-refresh] Starting tool refresh: dbType={}, scope={}", dbType, scope);
        return withTrackingWriteLock(() -> {
            DatabaseCatalog.DbDefinition def = requireDeployableDefinition(dbType);
            int updated = 0;

            Set<String> localRefs = scope == RefreshScope.LOCAL ? docker.getLocalImageReferences() : null;
            boolean includeHub = scope != RefreshScope.LOCAL;
            boolean forceHubCheck = scope != RefreshScope.LOCAL;

            for (String tag : resolveVersions(def, true)) {
                if (scope == RefreshScope.HUB && dockerHub.resolveDockerHubRepository(def.dockerImage()) == null) {
                    // Non-Hub images have no remote refresh source.
                    evaluateAndPersist(def.type(), tag, false, false, null);
                } else {
                    evaluateAndPersist(def.type(), tag, includeHub, forceHubCheck, localRefs);
                }
                updated++;
            }
            log.info("[image-refresh] Completed tool refresh: dbType={}, scope={}, updated={} tags",
                    dbType, scope, updated);
            return updated;
        });
    }

    @Transactional
    public int refreshLocalStatuses() {
        log.debug("[image-refresh] Starting local-only refresh for all tools");
        return withTrackingWriteLock(() -> {
            Set<String> localRefs = docker.getLocalImageReferences();
            int updated = 0;
            for (DatabaseCatalog.DbDefinition def : deployableCatalog()) {
                for (String tag : resolveVersions(def, false)) {
                    evaluateAndPersist(def.type(), tag, false, false, localRefs);
                    updated++;
                }
            }
            log.debug("[image-refresh] Completed local-only refresh: updated {} tags", updated);
            return updated;
        });
    }

    @Transactional
    public int refreshDockerHubStatuses() {
        log.debug("[image-refresh] Starting Docker Hub refresh for hub-managed tools");
        return withTrackingWriteLock(() -> {
            int updated = 0;
            for (DatabaseCatalog.DbDefinition def : deployableCatalog()) {
                if (dockerHub.resolveDockerHubRepository(def.dockerImage()) == null) continue;
                for (String tag : resolveVersions(def, false)) {
                    evaluateAndPersist(def.type(), tag, true, true);
                    updated++;
                }
            }
            log.debug("[image-refresh] Completed Docker Hub refresh: updated {} tags", updated);
            return updated;
        });
    }

    @Transactional
    public List<String> discoverAndTrackVersions(DbType dbType, boolean refresh) {
        log.info("[image-versions] Discovering versions: dbType={}, refresh={}", dbType, refresh);
        return withTrackingWriteLock(() -> {
            DatabaseCatalog.DbDefinition def = requireDeployableDefinition(dbType);
            List<String> versions = resolveVersions(def, refresh);
            ensureTrackedTags(def, versions);
            log.info("[image-versions] Version discovery complete: dbType={}, count={}", dbType, versions.size());
            return versions;
        });
    }

    private <T> T withTrackingWriteLock(java.util.function.Supplier<T> work) {
        trackingWriteLock.lock();
        try {
            return work.get();
        } finally {
            trackingWriteLock.unlock();
        }
    }

    private Collection<DatabaseCatalog.DbDefinition> deployableCatalog() {
        return DatabaseCatalog.all().stream()
                .filter(def -> def.dockerImage() != null)
                .toList();
    }

    private DatabaseCatalog.DbDefinition requireDeployableDefinition(DbType dbType) {
        DatabaseCatalog.DbDefinition def = DatabaseCatalog.get(dbType);
        if (def == null || def.dockerImage() == null) {
            throw new IllegalArgumentException("Unsupported deployable database type: " + dbType);
        }
        return def;
    }

    private List<String> resolveVersions(DatabaseCatalog.DbDefinition def, boolean refresh) {
        try {
            List<String> tags = imageTagVersionService.resolveVersions(def.type(), refresh);
            if (tags != null && !tags.isEmpty()) {
                return tags;
            }
        } catch (Exception e) {
            log.debug("Version discovery fallback for {}: {}", def.type(), e.getMessage());
        }
        return def.versions();
    }

    private List<ImageTrackingStatus> ensureTrackedTags(DatabaseCatalog.DbDefinition def, boolean refreshVersions) {
        List<ImageTrackingStatus> existing = trackingRepo.findByDbTypeOrderByImageNameAscImageTagAsc(def.type());
        if (!refreshVersions && !existing.isEmpty()) {
            return existing;
        }

        List<String> resolvedTags = resolveVersions(def, refreshVersions);
        return ensureTrackedTags(def, resolvedTags, existing);
    }

    private List<ImageTrackingStatus> ensureTrackedTags(DatabaseCatalog.DbDefinition def,
                                                        List<String> resolvedTags) {
        List<ImageTrackingStatus> existing = trackingRepo.findByDbTypeOrderByImageNameAscImageTagAsc(def.type());
        return ensureTrackedTags(def, resolvedTags, existing);
    }

    private List<ImageTrackingStatus> ensureTrackedTags(DatabaseCatalog.DbDefinition def,
                                                        List<String> resolvedTags,
                                                        List<ImageTrackingStatus> existing) {
        Set<String> existingTags = new HashSet<>();
        for (ImageTrackingStatus row : existing) {
            existingTags.add(row.getImageTag());
        }

        List<ImageTrackingStatus> newRows = new ArrayList<>();
        boolean hubManaged = dockerHub.resolveDockerHubRepository(def.dockerImage()) != null;
        for (String tag : resolvedTags) {
            if (existingTags.contains(tag)) {
                continue;
            }

            ImageTrackingStatus row = new ImageTrackingStatus();
            row.setId(UUID.randomUUID().toString());
            row.setDbType(def.type());
            row.setImageName(def.dockerImage());
            row.setImageTag(tag);
            row.setDockerHubManaged(hubManaged);
            row.setLocalStatus(ImageAvailabilityState.UNKNOWN);
            row.setDockerHubStatus(hubManaged ? ImageAvailabilityState.UNKNOWN : ImageAvailabilityState.NOT_APPLICABLE);
            row.setDecision(ImageValidationDecision.ALLOW_WITH_WARNING);
            row.setMessage("Version discovered from registry; checks pending");
            newRows.add(row);
        }

        if (!newRows.isEmpty()) {
            return withTrackingWriteLock(() -> {
                List<ImageTrackingStatus> current = trackingRepo.findByDbTypeOrderByImageNameAscImageTagAsc(def.type());
                Set<String> currentTags = new HashSet<>();
                for (ImageTrackingStatus row : current) {
                    currentTags.add(row.getImageTag());
                }

                List<ImageTrackingStatus> missing = new ArrayList<>();
                for (ImageTrackingStatus row : newRows) {
                    if (!currentTags.contains(row.getImageTag())) {
                        missing.add(row);
                    }
                }

                if (!missing.isEmpty()) {
                    trackingRepo.saveAll(missing);
                }

                return trackingRepo.findByDbTypeOrderByImageNameAscImageTagAsc(def.type());
            });
        }

        return existing;
    }

    private ImageCheckResponse evaluateAndPersist(DbType dbType,
                                                  String rawTag,
                                                  boolean includeHub,
                                                  boolean forceHubCheck) {
        return evaluateAndPersist(dbType, rawTag, includeHub, forceHubCheck, null);
    }

    private ImageCheckResponse evaluateAndPersist(DbType dbType,
                                                  String rawTag,
                                                  boolean includeHub,
                                                  boolean forceHubCheck,
                                                  Set<String> localRefs) {
        String tag = normalizeTag(rawTag);
        DatabaseCatalog.DbDefinition def = DatabaseCatalog.get(dbType);
        if (def == null || def.dockerImage() == null) {
            throw new IllegalArgumentException("Unsupported deployable database type: " + dbType);
        }

        String image = def.dockerImage();
        LocalDateTime now = LocalDateTime.now();

        Optional<ImageTrackingStatus> existing = trackingRepo.findByDbTypeAndImageNameAndImageTag(dbType, image, tag);

        boolean dockerHubManaged = dockerHub.resolveDockerHubRepository(image) != null;

        ImageAvailabilityState localStatus = checkLocalStatus(image, tag, localRefs);
        ImageAvailabilityState dockerHubStatus = existing
                .map(ImageTrackingStatus::getDockerHubStatus)
                .orElse(dockerHubManaged ? ImageAvailabilityState.UNKNOWN : ImageAvailabilityState.NOT_APPLICABLE);
        LocalDateTime dockerHubCheckedAt = existing
                .map(ImageTrackingStatus::getDockerHubCheckedAt)
                .orElse(null);

        if (!dockerHubManaged) {
            dockerHubStatus = ImageAvailabilityState.NOT_APPLICABLE;
        }

        if (includeHub && dockerHubManaged && (forceHubCheck || localStatus != ImageAvailabilityState.AVAILABLE)) {
            DockerHubTagClient.HubTagResult hubResult = dockerHub.checkTag(image, tag);
            dockerHubStatus = hubResult.status();
            dockerHubCheckedAt = now;
        }

        DecisionResult decisionResult = decide(localStatus, dockerHubStatus, dockerHubManaged, image, tag);

        ImageTrackingStatus row = existing.orElseGet(ImageTrackingStatus::new);
        if (row.getId() == null) row.setId(UUID.randomUUID().toString());
        row.setDbType(dbType);
        row.setImageName(image);
        row.setImageTag(tag);
        row.setDockerHubManaged(dockerHubManaged);
        row.setLocalStatus(localStatus);
        row.setDockerHubStatus(dockerHubStatus);
        row.setDecision(decisionResult.decision());
        row.setMessage(decisionResult.message());
        row.setLocalCheckedAt(now);
        row.setDockerHubCheckedAt(dockerHubCheckedAt);
        trackingRepo.save(row);

        log.debug("[image-check] {}:{} decision={}, local={}, hub={}, includeHub={}, forceHubCheck={}",
            dbType, tag, decisionResult.decision(), localStatus, dockerHubStatus, includeHub, forceHubCheck);

        return toResponse(row, def.displayName());
    }

    private ImageAvailabilityState checkLocalStatus(String image, String tag, Set<String> localRefs) {
        try {
            boolean available = localRefs != null
                    ? docker.hasLocalImage(image, tag, localRefs)
                    : docker.isImageAvailableLocally(image, tag);
            return available ? ImageAvailabilityState.AVAILABLE : ImageAvailabilityState.MISSING;
        } catch (Exception e) {
            log.warn("Local image check failed for {}:{}: {}", image, tag, e.getMessage());
            return ImageAvailabilityState.UNKNOWN;
        }
    }

    private record DecisionResult(ImageValidationDecision decision, String message) {}

    private DecisionResult decide(ImageAvailabilityState local,
                                  ImageAvailabilityState hub,
                                  boolean dockerHubManaged,
                                  String image,
                                  String tag) {
        String imageRef = image + ":" + tag;

        if (local == ImageAvailabilityState.AVAILABLE) {
            return new DecisionResult(ImageValidationDecision.ALLOW, "Image is available locally");
        }

        if (!dockerHubManaged) {
            return new DecisionResult(
                    ImageValidationDecision.ALLOW_WITH_WARNING,
                    "Remote validation skipped for non-Docker Hub image " + imageRef
            );
        }

        return switch (hub) {
            case AVAILABLE -> new DecisionResult(
                    ImageValidationDecision.ALLOW,
                    "Image tag exists on Docker Hub and will be pulled"
            );
            case MISSING -> new DecisionResult(
                    ImageValidationDecision.BLOCK,
                    "Image tag does not exist on Docker Hub: " + imageRef
            );
            case UNKNOWN -> new DecisionResult(
                    ImageValidationDecision.ALLOW_WITH_WARNING,
                    "Could not validate image on Docker Hub; deployment may fail if tag is invalid"
            );
            case NOT_APPLICABLE -> new DecisionResult(
                    ImageValidationDecision.ALLOW_WITH_WARNING,
                    "Docker Hub check not applicable for " + imageRef
            );
        };
    }

    private ImageCheckResponse toResponse(ImageTrackingStatus row, String displayName) {
        return new ImageCheckResponse(
                row.getDbType(),
                displayName,
                row.getImageName(),
                row.getImageTag(),
                row.getImageName() + ":" + row.getImageTag(),
                row.isDockerHubManaged(),
                row.getLocalStatus(),
                row.getDockerHubStatus(),
                row.getDecision(),
                row.getMessage(),
                row.getLocalCheckedAt(),
                row.getDockerHubCheckedAt(),
                row.getUpdatedAt()
        );
    }

    private ImageToolSummaryResponse toToolSummary(DatabaseCatalog.DbDefinition def,
                                                   List<ImageTrackingStatus> rows) {
        if (rows.isEmpty()) {
            int totalTags = resolveVersions(def, false).size();
            return new ImageToolSummaryResponse(
                    def.type(),
                    def.displayName(),
                    def.icon(),
                    def.dockerImage(),
                    totalTags,
                    0,
                    totalTags,
                    0,
                    0,
                    0,
                    null
            );
        }

        int allow = 0;
        int warning = 0;
        int blocked = 0;
        int localAvailable = 0;
        int hubAvailable = 0;
        LocalDateTime latest = null;

        for (ImageTrackingStatus row : rows) {
            switch (row.getDecision()) {
                case ALLOW -> allow++;
                case ALLOW_WITH_WARNING -> warning++;
                case BLOCK -> blocked++;
            }
            if (row.getLocalStatus() == ImageAvailabilityState.AVAILABLE) localAvailable++;
            if (row.getDockerHubStatus() == ImageAvailabilityState.AVAILABLE) hubAvailable++;

            LocalDateTime updated = row.getUpdatedAt();
            if (updated != null && (latest == null || updated.isAfter(latest))) latest = updated;
        }

        return new ImageToolSummaryResponse(
                def.type(),
                def.displayName(),
                def.icon(),
                def.dockerImage(),
                rows.size(),
                allow,
                warning,
                blocked,
                localAvailable,
                hubAvailable,
                latest
        );
    }

    private ImageToolDetailResponse buildToolDetail(DatabaseCatalog.DbDefinition def,
                                                    List<ImageCheckResponse> tags) {
        int allow = 0;
        int warning = 0;
        int blocked = 0;
        int localAvailable = 0;
        int hubAvailable = 0;
        LocalDateTime latest = null;

        for (ImageCheckResponse row : tags) {
            switch (row.decision()) {
                case ALLOW -> allow++;
                case ALLOW_WITH_WARNING -> warning++;
                case BLOCK -> blocked++;
            }

            if (row.localStatus() == ImageAvailabilityState.AVAILABLE) localAvailable++;
            if (row.dockerHubStatus() == ImageAvailabilityState.AVAILABLE) hubAvailable++;

            LocalDateTime updated = row.updatedAt();
            if (updated != null && (latest == null || updated.isAfter(latest))) latest = updated;
        }

        return new ImageToolDetailResponse(
                def.type(),
                def.displayName(),
                def.icon(),
                def.dockerImage(),
                tags.size(),
                allow,
                warning,
                blocked,
                localAvailable,
                hubAvailable,
                latest,
                tags
        );
    }

    private String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) return "latest";
        return tag.trim();
    }
}
