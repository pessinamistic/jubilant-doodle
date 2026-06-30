package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelSuggestionServiceTest {

  @Mock private GpuDetector gpuDetector;

  private ModelSuggestionService service() {
    return new ModelSuggestionService(gpuDetector);
  }

  private static final ModelDefinition SMALL =
      new ModelDefinition(
          "test:1b", "Test", ModelType.CHAT, 1, Quantization.Q4_K_M, 1000, 2000, "tiny");
  private static final ModelDefinition HUGE =
      new ModelDefinition(
          "test:70b", "Test", ModelType.CHAT, 70, Quantization.Q4_K_M, 42000, 48000, "huge");

  @Test
  void score_fast_when_vram_has_headroom() {
    var nvidia = new SystemProfile(GpuVendor.NVIDIA, 24000, 32000, 16, "Linux/amd64", true);
    assertThat(ModelSuggestionService.score(SMALL, nvidia)).isEqualTo(CompatibilityLevel.FAST);
  }

  @Test
  void score_ok_when_vram_is_tight() {
    // minVram 1000 * 1.3 = 1300; vram 1100 -> OK (>=1000 but <1300)
    var nvidia = new SystemProfile(GpuVendor.NVIDIA, 1100, 8000, 8, "Linux/amd64", true);
    assertThat(ModelSuggestionService.score(SMALL, nvidia)).isEqualTo(CompatibilityLevel.OK);
  }

  @Test
  void score_cpu_only_when_no_gpu_but_ram_fits() {
    var cpu = new SystemProfile(GpuVendor.NONE, 0, 8000, 8, "Linux/amd64", false);
    assertThat(ModelSuggestionService.score(SMALL, cpu)).isEqualTo(CompatibilityLevel.CPU_ONLY);
  }

  @Test
  void score_too_large_when_ram_insufficient() {
    var cpu = new SystemProfile(GpuVendor.NONE, 0, 8000, 8, "Linux/amd64", false);
    assertThat(ModelSuggestionService.score(HUGE, cpu)).isEqualTo(CompatibilityLevel.TOO_LARGE);
  }

  @Test
  void apple_silicon_scores_off_unified_memory() {
    // 24 GB Mac: effective VRAM = 24000, so an 8B model (minVram 5500) is FAST.
    var apple = new SystemProfile(GpuVendor.APPLE, 0, 24000, 10, "Mac OS X/aarch64", false);
    var llama8b = ModelCatalog.get("llama3.1:8b");
    assertThat(ModelSuggestionService.score(llama8b, apple)).isEqualTo(CompatibilityLevel.FAST);
  }

  @Test
  void suggestions_filter_by_type_and_compat_and_rank_best_first() {
    var nvidia = new SystemProfile(GpuVendor.NVIDIA, 6000, 16000, 16, "Linux/amd64", true);
    when(gpuDetector.detect()).thenReturn(nvidia);

    var chat = service().suggestions(ModelType.CHAT, Set.of(CompatibilityLevel.FAST));

    assertThat(chat).isNotEmpty();
    assertThat(chat).allSatisfy(s -> assertThat(s.model().type()).isEqualTo(ModelType.CHAT));
    assertThat(chat)
        .allSatisfy(s -> assertThat(s.compatibility()).isEqualTo(CompatibilityLevel.FAST));
    // Best-fit first means non-decreasing param size within the same compat level.
    for (int i = 1; i < chat.size(); i++) {
      assertThat(chat.get(i).model().paramsBillions())
          .isGreaterThanOrEqualTo(chat.get(i - 1).model().paramsBillions());
    }
  }

  @Test
  void suggestions_unfiltered_returns_whole_catalog() {
    when(gpuDetector.detect())
        .thenReturn(new SystemProfile(GpuVendor.NONE, 0, 64000, 16, "Linux/amd64", false));

    var all = service().suggestions(null, null);

    assertThat(all).hasSize(ModelCatalog.all().size());
  }
}
