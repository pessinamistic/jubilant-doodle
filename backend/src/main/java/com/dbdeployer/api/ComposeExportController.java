package com.dbdeployer.api;

import com.dbdeployer.service.ComposeExportService;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exports the current managed instances as a downloadable {@code docker-compose.yml}. */
@Slf4j
@RestController
@RequestMapping("/export")
public class ComposeExportController {

  private static final String DEFAULT_FILENAME = "docker-compose.yml";

  private final ComposeExportService composeExportService;

  public ComposeExportController(ComposeExportService composeExportService) {
    this.composeExportService = composeExportService;
  }

  /**
   * Returns a docker-compose.yml describing exportable instances.
   *
   * <p>With no {@code configIds}, describes all non-removed, non-system, Docker-deployed instances
   * (backward-compatible whole-stack export). With {@code configIds}, restricts the export to that
   * comma-separated set of config ids; unknown ids are silently ignored. When exactly one id is
   * given and it resolves to a known instance, the download filename becomes {@code
   * <instance-name>-compose.yml} instead of the generic name.
   */
  @GetMapping(value = "/docker-compose", produces = "application/x-yaml")
  public ResponseEntity<String> dockerCompose(
      @RequestParam(value = "configIds", required = false) String configIds) {
    List<String> ids = parseIds(configIds);
    log.info(
        "[api] docker-compose export requested{}",
        ids.isEmpty() ? "" : " for " + ids.size() + " instance(s)");

    String yaml = composeExportService.exportYaml(ids);
    String filename = ids.size() == 1 ? filenameFor(ids.get(0)) : DEFAULT_FILENAME;

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("application/x-yaml"))
        .body(yaml);
  }

  private String filenameFor(String configId) {
    return composeExportService
        .resolveInstanceName(configId)
        .map(name -> sanitizeFilenamePart(name) + "-compose.yml")
        .orElse(DEFAULT_FILENAME);
  }

  private static List<String> parseIds(String configIds) {
    if (configIds == null || configIds.isBlank()) {
      return List.of();
    }
    return Arrays.stream(configIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  /** Filesystem-safe slug for use in a Content-Disposition filename. */
  private static String sanitizeFilenamePart(String name) {
    String base = name.toLowerCase().replaceAll("[^a-z0-9_-]", "-").replaceAll("(^-+)|(-+$)", "");
    return base.isBlank() ? "instance" : base;
  }
}
