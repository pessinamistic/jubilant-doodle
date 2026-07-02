package com.dbdeployer.api;

import com.dbdeployer.runtime.ModelDashboardService;
import com.dbdeployer.runtime.OllamaAdminClient;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The model management dashboard API. Model names carry {@code :} and {@code /} (namespaced Ollama
 * tags), so state-changing operations take the model in a JSON body rather than a path variable.
 */
@Slf4j
@RestController
@RequestMapping("/models/runtime")
public class ModelRuntimeController {

  /** Body for model-scoped actions; settings fields only used by {@code /settings}. */
  public record ModelActionRequest(
      String model, Double temperature, Integer numCtx, String keepAlive) {}

  private final ModelDashboardService dashboard;

  public ModelRuntimeController(ModelDashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping
  public ModelDashboardService.RuntimeDashboard dashboard() {
    return dashboard.dashboard();
  }

  @PostMapping("/load")
  public ResponseEntity<OllamaAdminClient.AdminResult> load(@RequestBody ModelActionRequest req) {
    OllamaAdminClient.AdminResult result = dashboard.load(requireModel(req));
    return result.success()
        ? ResponseEntity.ok(result)
        : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
  }

  @PostMapping("/unload")
  public ResponseEntity<OllamaAdminClient.AdminResult> unload(@RequestBody ModelActionRequest req) {
    OllamaAdminClient.AdminResult result = dashboard.unload(requireModel(req));
    return result.success()
        ? ResponseEntity.ok(result)
        : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
  }

  @PostMapping("/delete")
  public ResponseEntity<OllamaAdminClient.AdminResult> delete(@RequestBody ModelActionRequest req) {
    OllamaAdminClient.AdminResult result = dashboard.deleteModel(requireModel(req));
    return result.success()
        ? ResponseEntity.ok(result)
        : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
  }

  @PostMapping("/settings")
  public ResponseEntity<Void> settings(@RequestBody ModelActionRequest req) {
    dashboard.updateSettings(
        requireModel(req),
        new ModelDashboardService.ModelSettings(req.temperature(), req.numCtx(), req.keepAlive()));
    return ResponseEntity.noContent().build();
  }

  /** Pulls run minutes-long; accepted and tracked in the dashboard's {@code pulls} map. */
  @PostMapping("/pull")
  public ResponseEntity<Void> pull(@RequestBody ModelActionRequest req) {
    dashboard.pullAsync(requireModel(req));
    return ResponseEntity.accepted().build();
  }

  private static String requireModel(ModelActionRequest req) {
    if (req == null || req.model() == null || req.model().isBlank()) {
      throw new IllegalArgumentException("model is required");
    }
    return req.model().trim();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
    log.warn("[api] bad request: {}", e.getMessage());
    return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
  }
}
