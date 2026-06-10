package com.dbdeployer.api;

import com.dbdeployer.deploy.DatabaseCatalog;
import com.dbdeployer.model.DbType;
import com.dbdeployer.service.ImageValidationService;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API flow for DB catalog metadata and version discovery. */
@Slf4j
@RestController
@RequestMapping("/catalog")
public class CatalogController {

  private final ImageValidationService imageValidationService;

  public CatalogController(
    ImageValidationService imageValidationService) {
    this.imageValidationService = imageValidationService;
  }

  /** List all supported database types with their catalog info. */
  @GetMapping
  public Collection<DatabaseCatalog.DbDefinition> catalog() {
    return DatabaseCatalog.all();
  }

  /**
   * Resolve deployable versions dynamically from the image registry for one tool.
   */
  @GetMapping("/{dbType}/versions")
  public List<String> catalogVersions(
    @PathVariable DbType dbType,
    @RequestParam(defaultValue = "false") boolean refresh) {
    log.info("[api] catalog versions requested: dbType={}, refresh={}", dbType, refresh);
    List<String> versions = imageValidationService.discoverAndTrackVersions(dbType, refresh);
    log.info("[api] catalog versions resolved: dbType={}, count={}", dbType, versions.size());
    return versions;
  }
}
