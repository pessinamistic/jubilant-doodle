package com.dbdeployer.api;

import com.dbdeployer.api.dto.ContainerMetricsResponse;
import com.dbdeployer.api.dto.DeployRequest;
import com.dbdeployer.api.dto.DiscoveredContainerDto;
import com.dbdeployer.api.dto.ImageCheckResponse;
import com.dbdeployer.api.dto.ImageToolDetailResponse;
import com.dbdeployer.api.dto.ImageToolSummaryResponse;
import com.dbdeployer.api.dto.ImportRequest;
import com.dbdeployer.api.dto.InstanceResponse;
import com.dbdeployer.api.dto.InstanceStatsResponse;
import com.dbdeployer.api.dto.PipelineResponse;
import com.dbdeployer.api.dto.ReImportRequest;
import com.dbdeployer.api.dto.SystemDbStatsResponse;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.service.ConfigTemplateService;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.service.DbInstanceService;
import com.dbdeployer.service.ImageValidationService;
import com.dbdeployer.service.SystemDbStatsService;
import com.dbdeployer.store.DeploymentConfigRepository;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DbInstanceController {

  private static final Logger log = LoggerFactory.getLogger(DbInstanceController.class);

  private final DbInstanceService service;
  private final ConnectionStringBuilder connBuilder;
  private final SystemDbStatsService statsService;
  private final ImageValidationService imageValidationService;
  private final ConfigTemplateService templateService;
  private final DeploymentConfigRepository configRepo;

  public DbInstanceController(
      DbInstanceService service,
      ConnectionStringBuilder connBuilder,
      SystemDbStatsService statsService,
      ImageValidationService imageValidationService,
      ConfigTemplateService templateService,
      DeploymentConfigRepository configRepo) {
    this.service = service;
    this.connBuilder = connBuilder;
    this.statsService = statsService;
    this.imageValidationService = imageValidationService;
    this.templateService = templateService;
    this.configRepo = configRepo;
  }

  /** List all deployed instances */
  @GetMapping("/instances")
  public List<InstanceResponse> list() {
    return service.listAll().stream().map(this::toResponse).toList();
  }

  /** Aggregate status counts — used by the overview stats panel */
  @GetMapping("/instances/stats")
  public InstanceStatsResponse stats() {
    return service.getStats();
  }

  /** Get a single instance */
  @GetMapping("/instances/{id}")
  public InstanceResponse get(@PathVariable String id) {
    return toResponse(service.getById(id));
  }

  /** Deploy a new database instance */
  @PostMapping("/instances")
  public ResponseEntity<InstanceResponse> deploy(@Valid @RequestBody DeployRequest req) {
    log.info(
        "[api] deploy requested: name='{}', dbType={}, version={}, hostPort={}",
        req.name(),
        req.dbType(),
        req.version(),
        req.hostPort());
    DeploymentConfig config = service.deploy(req);
    log.info("[api] deploy accepted: configId={}, name='{}'", config.getId(), config.getName());
    return ResponseEntity.accepted().body(toResponse(config));
  }

  /** Rename an instance */
  @PatchMapping("/instances/{id}")
  public InstanceResponse rename(@PathVariable String id, @RequestBody Map<String, String> body) {
    return toResponse(service.rename(id, body.get("name")));
  }

  /** Start a stopped instance */
  @PostMapping("/instances/{id}/start")
  public InstanceResponse start(@PathVariable String id) {
    return toResponse(service.startInstance(id));
  }

  /** Stop a running instance */
  @PostMapping("/instances/{id}/stop")
  public InstanceResponse stop(@PathVariable String id) {
    return toResponse(service.stopInstance(id));
  }

  /** Remove an instance (stop + delete container; keeps config + container rows in DB) */
  @DeleteMapping("/instances/{id}")
  public ResponseEntity<Void> remove(@PathVariable String id) {
    service.removeInstance(id);
    return ResponseEntity.noContent().build();
  }

  /** Untrack an imported instance — marks it UNTRACKED without touching the Docker container */
  @PostMapping("/instances/{id}/untrack")
  public ResponseEntity<Void> untrack(@PathVariable String id) {
    service.untrackInstance(id);
    return ResponseEntity.noContent().build();
  }

  /** Re-track a previously untracked instance — restores it to its live Docker status */
  @PostMapping("/instances/{id}/retrack")
  public InstanceResponse retrack(@PathVariable String id) {
    return toResponse(service.reTrackInstance(id));
  }

  /** Get container logs */
  @GetMapping("/instances/{id}/logs")
  public Map<String, String> logs(
      @PathVariable String id, @RequestParam(defaultValue = "100") int tail)
      throws InterruptedException {
    return Map.of("logs", service.getLogs(id, tail));
  }

  /** Live container metrics — CPU, memory, network/block I/O, port probe */
  @GetMapping("/instances/{id}/container-metrics")
  public ContainerMetricsResponse containerMetrics(@PathVariable String id) {
    return service.getContainerMetrics(id);
  }

  /** Get the latest deploy pipeline for an instance */
  @GetMapping("/instances/{id}/pipeline")
  public ResponseEntity<PipelineResponse> pipeline(@PathVariable String id) {
    PipelineResponse resp = service.getLatestPipeline(id);
    return resp != null ? ResponseEntity.ok(resp) : ResponseEntity.notFound().build();
  }

  /** Get connection string for an instance */
  @GetMapping("/instances/{id}/connection-string")
  public Map<String, String> connectionString(@PathVariable String id) {
    DeploymentConfig config = service.getById(id);
    return Map.of(
        "connectionString", connBuilder.build(config),
        "masked", connBuilder.buildMasked(config));
  }

  /** Discover running Docker containers that look like databases but are not yet tracked. */
  @GetMapping("/instances/discover")
  public List<DiscoveredContainerDto> discover() {
    return service.discoverContainers();
  }

  /**
   * Register a pre-existing Docker container as a managed instance without touching the container
   * itself.
   */
  @PostMapping("/instances/import")
  public ResponseEntity<InstanceResponse> importContainer(@RequestBody ImportRequest req) {
    DeploymentConfig config = service.importContainer(req);
    return ResponseEntity.ok(toResponse(config));
  }

  /**
   * Re-import a previously untracked (REMOVED) imported instance by binding it to a new Docker
   * container. All config metadata is preserved.
   */
  @PutMapping("/instances/{id}/reimport")
  public ResponseEntity<InstanceResponse> reImportInstance(
      @PathVariable String id, @Valid @RequestBody ReImportRequest req) {
    DeploymentConfig config = service.reImportInstance(id, req);
    return ResponseEntity.ok(toResponse(config));
  }

  /** List all supported database types with their catalog info */
  @GetMapping("/catalog")
  public Collection<DatabaseCatalog.DbDefinition> catalog() {
    return DatabaseCatalog.all();
  }

  /** Resolve deployable versions dynamically from the image registry for one tool. */
  @GetMapping("/catalog/{dbType}/versions")
  public List<String> catalogVersions(
      @PathVariable DbType dbType, @RequestParam(defaultValue = "false") boolean refresh) {
    log.info("[api] catalog versions requested: dbType={}, refresh={}", dbType, refresh);
    List<String> versions = imageValidationService.discoverAndTrackVersions(dbType, refresh);
    log.info("[api] catalog versions resolved: dbType={}, count={}", dbType, versions.size());
    return versions;
  }

  /** Check one database tool image tag against local Docker + Docker Hub fallback. */
  @GetMapping("/images/check")
  public ImageCheckResponse checkImage(
      @RequestParam DbType dbType,
      @RequestParam String tag,
      @RequestParam(defaultValue = "false") boolean refresh) {
    log.debug("[api] image check requested: dbType={}, tag={}, refresh={}", dbType, tag, refresh);
    ImageCheckResponse result = imageValidationService.check(dbType, tag, refresh);
    log.debug(
        "[api] image check result: dbType={}, tag={}, decision={}, local={}, hub={}",
        dbType,
        tag,
        result.decision(),
        result.localStatus(),
        result.dockerHubStatus());
    return result;
  }

  /** Get tracked image statuses across supported catalog tools/tags. */
  @GetMapping("/images/tracking")
  public List<ImageCheckResponse> imageTracking() {
    log.debug("[api] image tracking requested");
    List<ImageCheckResponse> rows = imageValidationService.getOverview();
    log.debug("[api] image tracking returned {} rows", rows.size());
    return rows;
  }

  /** Lightweight per-tool image summary for card-based image home UI. */
  @GetMapping("/images/summary")
  public List<ImageToolSummaryResponse> imageSummary() {
    log.debug("[api] image summary requested");
    List<ImageToolSummaryResponse> rows = imageValidationService.getToolSummaries();
    log.debug("[api] image summary returned {} tool rows", rows.size());
    return rows;
  }

  /** Per-tool image details loaded on demand for drill-down pages. */
  @GetMapping("/images/tools/{dbType}")
  public ImageToolDetailResponse imageToolDetails(
      @PathVariable DbType dbType, @RequestParam(defaultValue = "false") boolean refresh) {
    log.debug("[api] image tool detail requested: dbType={}, refresh={}", dbType, refresh);
    ImageToolDetailResponse detail = imageValidationService.getToolDetails(dbType, refresh);
    log.debug("[api] image tool detail returned: dbType={}, tags={}", dbType, detail.totalTags());
    return detail;
  }

  /** Manually refresh image statuses for one tool only. */
  @PostMapping("/images/tools/{dbType}/refresh")
  public Map<String, Object> refreshImageTool(
      @PathVariable DbType dbType, @RequestParam(defaultValue = "all") String scope) {
    log.info("[api] image tool refresh requested: dbType={}, scope={}", dbType, scope);
    var parsedScope = ImageValidationService.RefreshScope.from(scope);
    int updated = imageValidationService.refreshToolStatuses(dbType, parsedScope);
    log.info(
        "[api] image tool refresh completed: dbType={}, scope={}, updated={}",
        dbType,
        parsedScope.name().toLowerCase(),
        updated);
    return Map.of(
        "dbType", dbType,
        "scope", parsedScope.name().toLowerCase(),
        "updated", updated);
  }

  /** Manually refresh tracked image statuses. Scope: local, hub, or all. */
  @PostMapping("/images/refresh")
  public Map<String, Object> refreshImages(@RequestParam(defaultValue = "all") String scope) {
    log.info("[api] image refresh requested: scope={}", scope);
    var parsedScope = ImageValidationService.RefreshScope.from(scope);
    int updated = imageValidationService.refresh(parsedScope);
    log.info(
        "[api] image refresh completed: scope={}, updated={}",
        parsedScope.name().toLowerCase(),
        updated);
    return Map.of("scope", parsedScope.name().toLowerCase(), "updated", updated);
  }

  /** Get system info (OS, available tools) */
  @GetMapping("/system")
  public Object systemInfo() {
    return service.getSystemInfo();
  }

  /** Live stats for the system database (schema row counts, pool, JVM heap, uptime) */
  @GetMapping("/system/stats")
  public SystemDbStatsResponse systemStats() {
    return statsService.getStats();
  }

  /** Sync container statuses from Docker */
  @PostMapping("/instances/sync")
  public ResponseEntity<Void> sync() {
    log.info("[api] instance status sync requested");
    service.syncStatuses();
    log.info("[api] instance status sync completed");
    return ResponseEntity.ok().build();
  }

  // ── Error handler ──────────────────────────────────────────────────────────

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
    log.warn("[api] bad request: {}", e.getMessage());
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }

  // ── Helper ─────────────────────────────────────────────────────────────────

  private InstanceResponse toResponse(DeploymentConfig config) {
    var def = DatabaseCatalog.get(config.getDbType());
    String display = def != null ? def.displayName() : config.getDbType().name();
    String icon = def != null ? def.icon() : "🗄️";
    String conn = connBuilder.build(config);
    String masked = connBuilder.buildMasked(config);
    String templateId = config.getTemplateId();
    String templateName = templateId != null
        ? configRepo.findById(templateId).map(t -> t.getName()).orElse(null)
        : null;
    return InstanceResponse.from(config, config.getContainer(), conn, masked, display, icon, templateId, templateName);
  }

  private InstanceResponse toResponse(DeployedContainer container) {
    DeploymentConfig config = container.getConfig();
    var def = DatabaseCatalog.get(config.getDbType());
    String display = def != null ? def.displayName() : config.getDbType().name();
    String icon = def != null ? def.icon() : "🗄️";
    String conn = connBuilder.build(config);
    String masked = connBuilder.buildMasked(config);
    String templateId = config.getTemplateId();
    String templateName = templateId != null
        ? configRepo.findById(templateId).map(t -> t.getName()).orElse(null)
        : null;
    return InstanceResponse.from(config, container, conn, masked, display, icon, templateId, templateName);
  }
}
