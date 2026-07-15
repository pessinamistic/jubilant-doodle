package com.dbdeployer.api;

import com.dbdeployer.service.ComposeExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exports the current managed instances as a downloadable {@code docker-compose.yml}. */
@Slf4j
@RestController
@RequestMapping("/export")
public class ComposeExportController {

  private final ComposeExportService composeExportService;

  public ComposeExportController(ComposeExportService composeExportService) {
    this.composeExportService = composeExportService;
  }

  /** Returns a docker-compose.yml describing all non-removed, non-system instances. */
  @GetMapping(value = "/docker-compose", produces = "application/x-yaml")
  public ResponseEntity<String> dockerCompose() {
    log.info("[api] docker-compose export requested");
    String yaml = composeExportService.exportYaml();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"docker-compose.yml\"")
        .contentType(MediaType.parseMediaType("application/x-yaml"))
        .body(yaml);
  }
}
