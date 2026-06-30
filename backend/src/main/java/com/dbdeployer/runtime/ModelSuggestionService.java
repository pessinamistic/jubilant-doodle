package com.dbdeployer.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Scores every catalog model against the host's {@link SystemProfile} and produces ranked Model
 * Cookbook suggestions. Scoring follows the roadmap §6 formula verbatim.
 */
@Slf4j
@Service
public class ModelSuggestionService {

  private final GpuDetector gpuDetector;

  public ModelSuggestionService(GpuDetector gpuDetector) {
    this.gpuDetector = gpuDetector;
  }

  /** Live host profile (re-detected each call — detection is cheap). */
  public SystemProfile profile() {
    return gpuDetector.detect();
  }

  /**
   * Returns all catalog models, scored against the live profile, optionally filtered by model type
   * and compatibility level. Results are ordered best-fit first, then by parameter size.
   */
  public List<ModelSuggestion> suggestions(
      ModelType typeFilter, Set<CompatibilityLevel> compatFilter) {
    SystemProfile sys = profile();
    return ModelCatalog.all().stream()
        .filter(m -> typeFilter == null || m.type() == typeFilter)
        .map(
            m -> {
              CompatibilityLevel level = score(m, sys);
              return new ModelSuggestion(m, level, speedTier(level));
            })
        .filter(
            s ->
                compatFilter == null
                    || compatFilter.isEmpty()
                    || compatFilter.contains(s.compatibility()))
        .sorted(
            Comparator.comparingInt((ModelSuggestion s) -> rank(s.compatibility()))
                .thenComparingLong(s -> s.model().paramsBillions()))
        .toList();
  }

  /** Roadmap §6 scoring — Apple Silicon uses unified memory (total RAM) as effective VRAM. */
  static CompatibilityLevel score(ModelDefinition m, SystemProfile sys) {
    long effectiveVram = sys.effectiveVramMb();
    if (effectiveVram >= m.minVramMb() * 1.3) return CompatibilityLevel.FAST;
    if (effectiveVram >= m.minVramMb()) return CompatibilityLevel.OK;
    if (sys.totalRamMb() >= m.minRamMb()) return CompatibilityLevel.CPU_ONLY;
    return CompatibilityLevel.TOO_LARGE;
  }

  private static int rank(CompatibilityLevel level) {
    return switch (level) {
      case FAST -> 0;
      case OK -> 1;
      case CPU_ONLY -> 2;
      case TOO_LARGE -> 3;
    };
  }

  private static String speedTier(CompatibilityLevel level) {
    return switch (level) {
      case FAST -> "Fast (GPU, headroom)";
      case OK -> "OK (GPU, tight)";
      case CPU_ONLY -> "Slow (CPU only)";
      case TOO_LARGE -> "Won't fit";
    };
  }
}
