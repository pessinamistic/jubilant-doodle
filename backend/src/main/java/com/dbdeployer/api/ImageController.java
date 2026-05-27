package com.dbdeployer.api;

import com.dbdeployer.api.dto.ImageCheckResponse;
import com.dbdeployer.api.dto.ImageToolDetailResponse;
import com.dbdeployer.api.dto.ImageToolSummaryResponse;
import com.dbdeployer.model.DbType;
import com.dbdeployer.service.ImageValidationService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API flow for image validation and tracking operations. */
@RestController
@RequestMapping("/api/images")
public class ImageController {

  private static final Logger log = LoggerFactory.getLogger(ImageController.class);

  private final ImageValidationService imageValidationService;

  public ImageController(ImageValidationService imageValidationService) {
    this.imageValidationService = imageValidationService;
  }

  /** Check one database tool image tag against local Docker + Docker Hub fallback. */
  @GetMapping("/check")
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
  @GetMapping("/tracking")
  public List<ImageCheckResponse> imageTracking() {
    log.debug("[api] image tracking requested");
    List<ImageCheckResponse> rows = imageValidationService.getOverview();
    log.debug("[api] image tracking returned {} rows", rows.size());
    return rows;
  }

  /** Lightweight per-tool image summary for card-based image home UI. */
  @GetMapping("/summary")
  public List<ImageToolSummaryResponse> imageSummary() {
    log.debug("[api] image summary requested");
    List<ImageToolSummaryResponse> rows = imageValidationService.getToolSummaries();
    log.debug("[api] image summary returned {} tool rows", rows.size());
    return rows;
  }

  /** Per-tool image details loaded on demand for drill-down pages. */
  @GetMapping("/tools/{dbType}")
  public ImageToolDetailResponse imageToolDetails(
      @PathVariable DbType dbType, @RequestParam(defaultValue = "false") boolean refresh) {
    log.debug("[api] image tool detail requested: dbType={}, refresh={}", dbType, refresh);
    ImageToolDetailResponse detail = imageValidationService.getToolDetails(dbType, refresh);
    log.debug("[api] image tool detail returned: dbType={}, tags={}", dbType, detail.totalTags());
    return detail;
  }

  /** Manually refresh image statuses for one tool only. */
  @PostMapping("/tools/{dbType}/refresh")
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
  @PostMapping("/refresh")
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
}
