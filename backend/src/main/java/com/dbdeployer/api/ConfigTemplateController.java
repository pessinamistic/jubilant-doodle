package com.dbdeployer.api;

import com.dbdeployer.api.dto.ConfigTemplateRequest;
import com.dbdeployer.api.dto.ConfigTemplateResponse;
import com.dbdeployer.api.dto.DeployFromTemplateRequest;
import com.dbdeployer.api.dto.InstanceResponse;
import com.dbdeployer.deploy.ConnectionStringBuilder;
import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.service.ConfigTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class ConfigTemplateController {

  private final ConfigTemplateService templateService;
  private final ConnectionStringBuilder connBuilder;

  public ConfigTemplateController(
      ConfigTemplateService templateService, ConnectionStringBuilder connBuilder) {
    this.templateService = templateService;
    this.connBuilder = connBuilder;
  }

  /** List all saved configuration templates, newest first. */
  @GetMapping
  public List<ConfigTemplateResponse> list() {
    return templateService.listAll().stream().map(ConfigTemplateResponse::from).toList();
  }

  /** Get a single template by ID. */
  @GetMapping("/{id}")
  public ConfigTemplateResponse get(@PathVariable String id) {
    return ConfigTemplateResponse.from(templateService.getById(id));
  }

  /** Save a new configuration template (no Docker action). */
  @PostMapping
  public ResponseEntity<ConfigTemplateResponse> create(
      @Valid @RequestBody ConfigTemplateRequest req) {
    return ResponseEntity.ok(ConfigTemplateResponse.from(templateService.create(req)));
  }

  /** Update an existing template. Does not affect instances already deployed from it. */
  @PutMapping("/{id}")
  public ConfigTemplateResponse update(
      @PathVariable String id, @Valid @RequestBody ConfigTemplateRequest req) {
    return ConfigTemplateResponse.from(templateService.update(id, req));
  }

  /** Delete a template. Deployed instances retain their templateId value but carry on. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    templateService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deploy a new instance from this template. The request supplies the per-deployment overrides
   * (instance name + host port). Returns 202 Accepted with the new instance response.
   */
  @PostMapping("/{id}/deploy")
  public ResponseEntity<InstanceResponse> deploy(
      @PathVariable String id, @Valid @RequestBody DeployFromTemplateRequest req) {
    DeploymentConfig config = templateService.deployFromTemplate(id, req);
    var def = DatabaseCatalog.get(config.getDbType());
    String display = def != null ? def.displayName() : config.getDbType().name();
    String icon = def != null ? def.icon() : "🗄️";
    String templateName = templateService.getById(id).getName();
    return ResponseEntity.accepted()
        .body(
            InstanceResponse.from(
                config,
                config.getContainer(),
                connBuilder.build(config),
                connBuilder.buildMasked(config),
                display,
                icon,
                id,
                templateName));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
  }
}
