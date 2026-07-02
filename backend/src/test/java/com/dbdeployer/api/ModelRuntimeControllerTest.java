package com.dbdeployer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dbdeployer.runtime.ModelDashboardService;
import com.dbdeployer.runtime.OllamaAdminClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ModelRuntimeControllerTest {

  @Mock private ModelDashboardService dashboard;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ModelRuntimeController(dashboard)).build();
  }

  @Test
  void dashboard_returns_runtime_state() throws Exception {
    when(dashboard.dashboard())
        .thenReturn(
            new ModelDashboardService.RuntimeDashboard(
                "http://localhost:11434",
                "NONE",
                true,
                "my-ollama",
                "RUNNING",
                List.of(),
                Map.of()));

    mockMvc
        .perform(get("/models/runtime"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseUrl").value("http://localhost:11434"))
        .andExpect(jsonPath("$.reachable").value(true))
        .andExpect(jsonPath("$.managedInstanceName").value("my-ollama"));
  }

  @Test
  void load_returns_bad_gateway_when_the_runtime_call_fails() throws Exception {
    when(dashboard.load("llama3.1:8b"))
        .thenReturn(new OllamaAdminClient.AdminResult(false, "connection refused"));

    mockMvc
        .perform(
            post("/models/runtime/load")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"llama3.1:8b\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void settings_updates_and_returns_no_content() throws Exception {
    mockMvc
        .perform(
            post("/models/runtime/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"llama3.1:8b\",\"temperature\":0.4,\"numCtx\":8192}"))
        .andExpect(status().isNoContent());

    verify(dashboard)
        .updateSettings("llama3.1:8b", new ModelDashboardService.ModelSettings(0.4, 8192, null));
  }

  @Test
  void pull_is_accepted_and_runs_async() throws Exception {
    mockMvc
        .perform(
            post("/models/runtime/pull")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"qwen2.5:7b\"}"))
        .andExpect(status().isAccepted());

    verify(dashboard).pullAsync("qwen2.5:7b");
  }

  @Test
  void blank_model_is_a_bad_request() throws Exception {
    mockMvc
        .perform(
            post("/models/runtime/load")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"model\":\"  \"}"))
        .andExpect(status().isBadRequest());

    verify(dashboard, org.mockito.Mockito.never()).load(any());
  }
}
