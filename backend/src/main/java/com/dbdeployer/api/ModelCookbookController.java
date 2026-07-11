package com.dbdeployer.api;

import com.dbdeployer.runtime.CompatibilityLevel;
import com.dbdeployer.runtime.ModelSuggestion;
import com.dbdeployer.runtime.ModelSuggestionService;
import com.dbdeployer.runtime.ModelType;
import com.dbdeployer.runtime.OllamaLibraryModel;
import com.dbdeployer.runtime.OllamaLibrarySearchService;
import com.dbdeployer.runtime.SystemProfile;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Model Cookbook API — "what can my machine actually run?". */
@Slf4j
@RestController
@RequestMapping("/models")
public class ModelCookbookController {

  private final ModelSuggestionService suggestionService;
  private final OllamaLibrarySearchService librarySearchService;

  public ModelCookbookController(
      ModelSuggestionService suggestionService, OllamaLibrarySearchService librarySearchService) {
    this.suggestionService = suggestionService;
    this.librarySearchService = librarySearchService;
  }

  /** Detected hardware profile (GPU vendor, VRAM, RAM, cores, platform). */
  @GetMapping("/profile")
  public SystemProfile profile() {
    return suggestionService.profile();
  }

  /**
   * Ranked model suggestions, optionally filtered by {@code type} (CHAT/CODE/EMBEDDING/VISION/
   * REASONING) and a comma-separated {@code compat} list (FAST,OK,CPU_ONLY,TOO_LARGE).
   */
  @GetMapping("/suggestions")
  public List<ModelSuggestion> suggestions(
      @RequestParam(required = false) ModelType type,
      @RequestParam(required = false) String compat) {
    Set<CompatibilityLevel> compatFilter =
        (compat == null || compat.isBlank())
            ? Set.of()
            : java.util.Arrays.stream(compat.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> CompatibilityLevel.valueOf(s.toUpperCase()))
                .collect(Collectors.toSet());
    log.info("[api] model suggestions requested: type={}, compat={}", type, compatFilter);
    return suggestionService.suggestions(type, compatFilter);
  }

  /**
   * Fallback discovery for models not in the curated catalog — proxies a community Ollama library
   * index (see {@link OllamaLibrarySearchService}) so a search with no local matches still returns
   * something pullable. Best-effort: returns an empty list rather than an error on failure.
   */
  @GetMapping("/library-search")
  public List<OllamaLibraryModel> librarySearch(
      @RequestParam String q, @RequestParam(required = false, defaultValue = "15") int limit) {
    log.info("[api] ollama library search requested: q={}, limit={}", q, limit);
    return librarySearchService.search(q, limit);
  }
}
