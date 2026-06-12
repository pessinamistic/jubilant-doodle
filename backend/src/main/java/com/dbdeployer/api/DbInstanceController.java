package com.dbdeployer.api;

import com.dbdeployer.api.dto.ConfigTemplateRequest;
import com.dbdeployer.api.dto.ContainerMetricsResponse;
import com.dbdeployer.api.dto.DiscoveredContainerDto;
import com.dbdeployer.api.dto.ImportRequest;
import com.dbdeployer.api.dto.InstanceResponse;
import com.dbdeployer.api.dto.InstanceStatsResponse;
import com.dbdeployer.api.dto.PipelineResponse;
import com.dbdeployer.api.dto.ReImportRequest;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.DeploymentResponse;
import com.dbdeployer.service.ConfigTemplateService;
import com.dbdeployer.service.DbInstanceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@RestController
@RequestMapping("/instances")
public class DbInstanceController {

  private final DbInstanceService service;
  private final ConnectionStringBuilder connBuilder;
  private final InstanceResponseAssembler responseAssembler;
  private final ConfigTemplateService configTemplateService;

  public DbInstanceController(
    DbInstanceService service,
    ConnectionStringBuilder connBuilder,
    InstanceResponseAssembler responseAssembler,
    ConfigTemplateService configTemplateService) {
    this.service = service;
    this.connBuilder = connBuilder;
    this.responseAssembler = responseAssembler;
    this.configTemplateService = configTemplateService;
  }

  /**
   * List all deployed instances
   */
  @GetMapping("")
  public List<InstanceResponse> list() {
    return service.listAll().stream().map(responseAssembler::fromContainer).toList();
  }

  /**
   * Aggregate status counts — used by the overview stats panel
   */
  @GetMapping("/stats")
  public InstanceStatsResponse stats() {
    return service.getStats();
  }

  /**
   * Get a single instance
   */
  @GetMapping("/{id}")
  public InstanceResponse get(
    @PathVariable String id) {
    return responseAssembler.fromContainer(service.getById(id));
  }

  /**
   * Deploy a new database instance
   */
  @PostMapping("")
  public ResponseEntity<InstanceResponse> deploy(
    @Valid @RequestBody ConfigTemplateRequest req) {
    log.info("[api] deploy requested: name='{}', dbType={}, version={}, hostPort={}", req.name(), req.dbType(),
        req.version(), req.hostPort());

    DeploymentConfig deploymentConfig = configTemplateService.create(req);
    DeploymentResponse deploymentResponse = service.deploy(req,
        deploymentConfig,
        false);
    deploymentConfig = deploymentResponse.getDeploymentConfig();
    log.info("[api] deploy accepted: configId={}, name='{}'", deploymentConfig.getId(), deploymentConfig.getName());
    return ResponseEntity.accepted().body(responseAssembler.fromConfig(deploymentResponse));
  }

  /**
   * Rename an instance
   */
  @PatchMapping("/{id}")
  public InstanceResponse rename(
    @PathVariable String id,
    @RequestBody Map<String, String> body) {
    DeploymentResponse deploymentResponse = service.rename(id, body.get("name"));
    return responseAssembler.fromConfig(deploymentResponse);
  }

  /**
   * Start a stopped instance
   */
  @PostMapping("/{id}/start")
  public InstanceResponse start(
    @PathVariable String id) {
    return responseAssembler.fromConfig(service.startInstance(id));
  }

  /**
   * Stop a running instance
   */
  @PostMapping("/{id}/stop")
  public InstanceResponse stop(
    @PathVariable String id) {
    return responseAssembler.fromConfig(service.stopInstance(id));
  }

  /**
   * Remove an instance (stop + delete container; keeps config + container rows in
   * DB)
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> remove(
    @PathVariable String id) {
    service.removeInstance(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Untrack an imported instance — marks it UNTRACKED without touching the Docker
   * container
   */
  @PostMapping("/{id}/untrack")
  public ResponseEntity<Void> untrack(
    @PathVariable String id) {
    service.untrackInstance(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Re-track a previously untracked instance — restores it to its live Docker
   * status
   */
  @PostMapping("/{id}/retrack")
  public InstanceResponse retrack(
    @PathVariable String id) {
    return responseAssembler.fromConfig(service.reTrackInstance(id));
  }

  /**
   * Get container logs
   */
  @GetMapping("/{id}/logs")
  public Map<String, String> logs(
    @PathVariable String id,
    @RequestParam(defaultValue = "100") int tail)
      throws InterruptedException {
    return Map.of("logs", service.getLogs(id, tail));
  }

  /**
   * Live container metrics — CPU, memory, network/block I/O, port probe
   */
  @GetMapping("/{id}/container-metrics")
  public ContainerMetricsResponse containerMetrics(
    @PathVariable String id) {
    return service.getContainerMetrics(id);
  }

  /**
   * Get the latest deploy pipeline for an instance
   */
  @GetMapping("/{id}/pipeline")
  public ResponseEntity<PipelineResponse> pipeline(
    @PathVariable String id) {
    PipelineResponse resp = service.getLatestPipeline(id);
    return resp != null ? ResponseEntity.ok(resp) : ResponseEntity.notFound().build();
  }

  /**
   * Get connection string for an instance
   */
  @GetMapping("/{id}/connection-string")
  public Map<String, String> connectionString(
    @PathVariable String id) {
    DeploymentConfig config = configTemplateService.getById(id, true);
    return Map.of("connectionString", connBuilder.build(config),
        "masked", connBuilder.buildMasked(config));
  }

  /**
   * Discover running Docker containers that look like databases but are not yet
   * tracked.
   */
  @GetMapping("/discover")
  public List<DiscoveredContainerDto> discover() {
    return service.discoverContainers();
  }

  /**
   * Register a pre-existing Docker container as a managed instance without
   * touching the container itself.
   */
  @PostMapping("/import")
  public ResponseEntity<InstanceResponse> importContainer(
    @RequestBody ImportRequest req) {
    return ResponseEntity.ok(responseAssembler.fromConfig(service.importContainer(req)));
  }

  /**
   * Re-import a previously untracked (REMOVED) imported instance by binding it to
   * a new Docker container. All config metadata is preserved.
   */
  @PutMapping("/{id}/reimport")
  public ResponseEntity<InstanceResponse> reImportInstance(
    @PathVariable String id,
    @Valid @RequestBody ReImportRequest req) {
    return ResponseEntity.ok(responseAssembler.fromConfig(service.reImportInstance(id, req)));
  }

  /**
   * Sync container statuses from Docker
   */
  @PostMapping("/sync")
  public ResponseEntity<Void> sync() {
    log.info("[api] instance status sync requested");
    service.syncStatuses();
    log.info("[api] instance status sync completed");
    return ResponseEntity.ok().build();
  }

  // ── Error handler ──────────────────────────────────────────────────────────

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(
    IllegalArgumentException e) {
    log.warn("[api] bad request: {}", e.getMessage());
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }
}
