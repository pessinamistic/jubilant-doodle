package com.dbdeployer.api;

import com.dbdeployer.runtime.CompatibilityLevel;
import com.dbdeployer.runtime.ModelSuggestion;
import com.dbdeployer.runtime.ModelSuggestionService;
import com.dbdeployer.runtime.ModelType;
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

  public ModelCookbookController(ModelSuggestionService suggestionService) {
    this.suggestionService = suggestionService;
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
}
