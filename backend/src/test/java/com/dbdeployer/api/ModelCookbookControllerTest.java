package com.dbdeployer.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbdeployer.runtime.CompatibilityLevel;
import com.dbdeployer.runtime.GpuVendor;
import com.dbdeployer.runtime.ModelCatalog;
import com.dbdeployer.runtime.ModelSuggestion;
import com.dbdeployer.runtime.ModelSuggestionService;
import com.dbdeployer.runtime.ModelType;
import com.dbdeployer.runtime.SystemProfile;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ModelCookbookControllerTest {

  @Mock private ModelSuggestionService suggestionService;

  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ModelCookbookController(suggestionService)).build();
  }

  @Test
  void profile_endpoint_returns_system_profile() throws Exception {
    when(suggestionService.profile())
        .thenReturn(new SystemProfile(GpuVendor.NVIDIA, 24000, 32000, 16, "Linux/amd64", true));

    mockMvc
        .perform(get("/models/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gpuVendor").value("NVIDIA"))
        .andExpect(jsonPath("$.vramMb").value(24000))
        .andExpect(jsonPath("$.containerGpu").value(true));
  }

  @Test
  void suggestions_endpoint_passes_type_and_compat_filters() throws Exception {
    var model = ModelCatalog.get("llama3.1:8b");
    when(suggestionService.suggestions(ModelType.CHAT, Set.of(CompatibilityLevel.FAST)))
        .thenReturn(
            List.of(new ModelSuggestion(model, CompatibilityLevel.FAST, "Fast (GPU, headroom)")));

    mockMvc
        .perform(get("/models/suggestions?type=CHAT&compat=FAST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].model.ollamaTag").value("llama3.1:8b"))
        .andExpect(jsonPath("$[0].compatibility").value("FAST"));
  }
}
