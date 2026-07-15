package com.dbdeployer.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.model.ModelRuntimeEntity;
import com.dbdeployer.model.PulledModel;
import com.dbdeployer.store.PulledModelRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelDashboardServiceTest {

  private static final String URL = "http://localhost:11434";

  @Mock private OllamaAdminClient admin;
  @Mock private ModelRuntimeService runtimes;
  @Mock private PulledModelRepository pulledRepo;
  @Mock private OllamaModelPuller puller;

  private ModelDashboardService service;
  private ModelRuntimeEntity runtimeRow;

  @BeforeEach
  void setUp() {
    service = new ModelDashboardService(admin, runtimes, pulledRepo, puller);
    runtimeRow = new ModelRuntimeEntity();
    runtimeRow.setId("rt-1");
    runtimeRow.setBaseUrl(URL);
    runtimeRow.setRuntimeType(ModelRuntime.OLLAMA);
    runtimeRow.setGpuVendor(GpuVendor.NONE);
    lenient().when(runtimes.resolveOllamaBaseUrl()).thenReturn(URL);
    lenient().when(runtimes.ensureRuntimeRow(URL)).thenReturn(runtimeRow);
    lenient().when(runtimes.findRunningOllama()).thenReturn(Optional.empty());
  }

  private static PulledModel row(String name) {
    PulledModel m = new PulledModel();
    m.setId("pm-" + name);
    m.setRuntimeId("rt-1");
    m.setModelName(name);
    m.setSizeBytes(100L);
    return m;
  }

  @Test
  void dashboard_marks_resident_models_and_carries_settings() throws Exception {
    when(admin.listLocal(URL))
        .thenReturn(
            List.of(new OllamaAdminClient.LocalModel("llama3.1:8b", 100, "d", "Q4_K_M", "8B")));
    when(admin.listLoaded(URL))
        .thenReturn(
            List.of(new OllamaAdminClient.LoadedModel("llama3.1:8b", 200, 150, "2026-07-02")));
    PulledModel stored = row("llama3.1:8b");
    stored.setTemperature(0.4);
    stored.setNumCtx(8192);
    when(pulledRepo.findByRuntimeIdAndModelName("rt-1", "llama3.1:8b"))
        .thenReturn(Optional.of(stored));
    when(pulledRepo.findByRuntimeId("rt-1")).thenReturn(List.of(stored));

    ModelDashboardService.RuntimeDashboard dash = service.dashboard();

    assertThat(dash.reachable()).isTrue();
    assertThat(dash.baseUrl()).isEqualTo(URL);
    assertThat(dash.models()).hasSize(1);
    var view = dash.models().get(0);
    assertThat(view.loaded()).isTrue();
    assertThat(view.sizeVramBytes()).isEqualTo(150);
    assertThat(view.temperature()).isEqualTo(0.4);
    assertThat(view.numCtx()).isEqualTo(8192);
  }

  @Test
  void dashboard_degrades_to_stored_rows_when_runtime_unreachable() throws Exception {
    when(admin.listLocal(URL)).thenThrow(new IllegalStateException("connection refused"));
    when(pulledRepo.findByRuntimeId("rt-1")).thenReturn(List.of(row("qwen2.5:7b")));

    ModelDashboardService.RuntimeDashboard dash = service.dashboard();

    assertThat(dash.reachable()).isFalse();
    assertThat(dash.models()).hasSize(1);
    assertThat(dash.models().get(0).loaded()).isFalse();
  }

  @Test
  void sync_prunes_models_deleted_outside_port_wrangler() throws Exception {
    when(admin.listLocal(URL)).thenReturn(List.of());
    when(admin.listLoaded(URL)).thenReturn(List.of());
    PulledModel stale = row("gone:latest");
    when(pulledRepo.findByRuntimeId("rt-1")).thenReturn(List.of(stale)).thenReturn(List.of());

    service.dashboard();

    verify(pulledRepo).delete(stale);
  }

  @Test
  void load_uses_saved_keep_alive_defaulting_to_resident() {
    when(pulledRepo.findFirstByModelNameOrderByPulledAtDesc("m1")).thenReturn(Optional.empty());
    when(admin.load(URL, "m1", "-1"))
        .thenReturn(new OllamaAdminClient.AdminResult(true, "load ok: m1"));

    assertThat(service.load("m1").success()).isTrue();
    verify(admin).load(URL, "m1", "-1");

    PulledModel withKeepAlive = row("m2");
    withKeepAlive.setKeepAlive("10m");
    when(pulledRepo.findFirstByModelNameOrderByPulledAtDesc("m2"))
        .thenReturn(Optional.of(withKeepAlive));
    when(admin.load(URL, "m2", "10m"))
        .thenReturn(new OllamaAdminClient.AdminResult(true, "load ok: m2"));

    assertThat(service.load("m2").success()).isTrue();
    verify(admin).load(URL, "m2", "10m");
  }

  @Test
  void deleteModel_removes_the_stored_row_only_on_success() {
    when(admin.delete(URL, "old:7b")).thenReturn(new OllamaAdminClient.AdminResult(false, "boom"));

    assertThat(service.deleteModel("old:7b").success()).isFalse();
    verify(pulledRepo, never()).delete(any(PulledModel.class));

    PulledModel stored = row("old:7b");
    when(admin.delete(URL, "old:7b")).thenReturn(new OllamaAdminClient.AdminResult(true, "ok"));
    when(pulledRepo.findByRuntimeIdAndModelName("rt-1", "old:7b")).thenReturn(Optional.of(stored));

    assertThat(service.deleteModel("old:7b").success()).isTrue();
    verify(pulledRepo).delete(stored);
  }

  @Test
  void updateSettings_creates_a_row_for_a_not_yet_synced_model() {
    when(pulledRepo.findByRuntimeIdAndModelName("rt-1", "new:3b")).thenReturn(Optional.empty());

    service.updateSettings("new:3b", new ModelDashboardService.ModelSettings(0.9, 4096, "15m"));

    var captor = ArgumentCaptor.forClass(PulledModel.class);
    verify(pulledRepo).save(captor.capture());
    PulledModel saved = captor.getValue();
    assertThat(saved.getModelName()).isEqualTo("new:3b");
    assertThat(saved.getTemperature()).isEqualTo(0.9);
    assertThat(saved.getNumCtx()).isEqualTo(4096);
    assertThat(saved.getKeepAlive()).isEqualTo("15m");
  }

  @Test
  void pullAsync_reports_failures_in_the_pull_status_map() throws Exception {
    when(puller.pull(eq(URL), eq("bad:model"), any()))
        .thenReturn(new OllamaModelPuller.PullResult(false, "no such model"));

    service.pullAsync("bad:model");

    // The pull runs on a virtual thread; poll briefly for the terminal state.
    ModelDashboardService.PullState status = null;
    for (int i = 0; i < 50; i++) {
      status = dashboardPulls().get("bad:model");
      if (status != null && "failed".equals(status.state())) break;
      Thread.sleep(20);
    }
    assertThat(status).isNotNull();
    assertThat(status.state()).isEqualTo("failed");
    assertThat(status.status()).contains("no such model");
  }

  private java.util.Map<String, ModelDashboardService.PullState> dashboardPulls() throws Exception {
    when(admin.listLocal(URL)).thenReturn(List.of());
    when(admin.listLoaded(URL)).thenReturn(List.of());
    when(pulledRepo.findByRuntimeId("rt-1")).thenReturn(List.of());
    return service.dashboard().pulls();
  }
}
