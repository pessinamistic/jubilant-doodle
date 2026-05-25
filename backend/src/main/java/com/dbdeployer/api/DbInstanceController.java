package com.dbdeployer.api;

import com.dbdeployer.api.dto.*;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.DbInstance;
import com.dbdeployer.service.DbInstanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DbInstanceController {

    private final DbInstanceService service;
    private final ConnectionStringBuilder connBuilder;

    public DbInstanceController(DbInstanceService service, ConnectionStringBuilder connBuilder) {
        this.service     = service;
        this.connBuilder = connBuilder;
    }

    /** List all deployed instances */
    @GetMapping("/instances")
    public List<InstanceResponse> list() {
        return service.listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Get a single instance */
    @GetMapping("/instances/{id}")
    public InstanceResponse get(@PathVariable String id) {
        return toResponse(service.getById(id));
    }

    /** Deploy a new database instance */
    @PostMapping("/instances")
    public ResponseEntity<InstanceResponse> deploy(@Valid @RequestBody DeployRequest req) {
        DbInstance instance = service.deploy(req);
        return ResponseEntity.accepted().body(toResponse(instance));
    }

    /** Rename an instance */
    @PatchMapping("/instances/{id}")
    public InstanceResponse rename(@PathVariable String id,
                                   @RequestBody java.util.Map<String, String> body) {
        String newName = body.get("name");
        return toResponse(service.rename(id, newName));
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

    /** Remove an instance (stop + delete container + remove from DB) */
    @DeleteMapping("/instances/{id}")
    public ResponseEntity<Void> remove(@PathVariable String id) {
        service.removeInstance(id);
        return ResponseEntity.noContent().build();
    }

    /** Get container logs */
    @GetMapping("/instances/{id}/logs")
    public Map<String, String> logs(@PathVariable String id,
                                    @RequestParam(defaultValue = "100") int tail) throws InterruptedException {
        return Map.of("logs", service.getLogs(id, tail));
    }

    /** Get connection string for an instance */
    @GetMapping("/instances/{id}/connection-string")
    public Map<String, String> connectionString(@PathVariable String id) {
        DbInstance instance = service.getById(id);
        return Map.of(
                "connectionString", connBuilder.build(instance),
                "masked",           connBuilder.buildMasked(instance)
        );
    }

    /**
     * Discover running Docker containers that look like databases but are not
     * yet tracked in db_instances.
     */
    @GetMapping("/instances/discover")
    public List<DiscoveredContainerDto> discover() {
        return service.discoverContainers();
    }

    /**
     * Register a pre-existing Docker container as a managed db_instance row
     * without touching the container itself.
     */
    @PostMapping("/instances/import")
    public ResponseEntity<InstanceResponse> importContainer(@RequestBody ImportRequest req) {
        DbInstance imported = service.importContainer(req);
        return ResponseEntity.ok(toResponse(imported));
    }

    /** List all supported database types with their catalog info */
    @GetMapping("/catalog")
    public Collection<DatabaseCatalog.DbDefinition> catalog() {
        return DatabaseCatalog.all();
    }

    /** Get system info (OS, available tools) */
    @GetMapping("/system")
    public Object systemInfo() {
        return service.getSystemInfo();
    }

    /** Sync container statuses from Docker */
    @PostMapping("/instances/sync")
    public ResponseEntity<Void> sync() {
        service.syncStatuses();
        return ResponseEntity.ok().build();
    }

    // ── Error handler ──────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private InstanceResponse toResponse(DbInstance instance) {
        var def    = DatabaseCatalog.get(instance.getDbType());
        String displayName = def != null ? def.displayName() : instance.getDbType().name();
        String icon        = def != null ? def.icon()        : "🗄️";
        String conn        = connBuilder.build(instance);
        String masked      = connBuilder.buildMasked(instance);
        return InstanceResponse.from(instance, conn, masked, displayName, icon);
    }
}
