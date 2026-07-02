package com.dbdeployer.runtime;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.ModelRuntimeEntity;
import com.dbdeployer.model.PulledModel;
import com.dbdeployer.store.PulledModelRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The model management dashboard (Odysseus-inspired): one view over the active Ollama runtime —
 * which models are on disk, which are resident in memory (with RAM/VRAM footprint), and each
 * model's saved inference settings. Load ("run") and unload ("pause") are memory-residency
 * operations against the single runtime container, never container lifecycle.
 *
 * <p>Pulls are long (gigabytes), so {@link #pullAsync} runs them on a virtual thread and the
 * dashboard reports in-flight pulls; the model appears in the list once the runtime lists it.
 */
@Slf4j
@Service
public class ModelDashboardService {

  /** Per-model inference settings; a null field means "runtime default". */
  public record ModelSettings(Double temperature, Integer numCtx, String keepAlive) {}

  /** One model row in the dashboard. */
  public record RuntimeModelView(
      String name,
      long sizeBytes,
      String quantization,
      String digest,
      boolean loaded,
      long sizeVramBytes,
      String expiresAt,
      Double temperature,
      Integer numCtx,
      String keepAlive) {}

  /** The whole dashboard payload. */
  public record RuntimeDashboard(
      String baseUrl,
      String gpuVendor,
      boolean reachable,
      String managedInstanceName,
      String managedInstanceStatus,
      List<RuntimeModelView> models,
      Map<String, String> pulls) {}

  private final OllamaAdminClient admin;
  private final ModelRuntimeService runtimes;
  private final PulledModelRepository pulledRepo;
  private final OllamaModelPuller puller;

  /** In-flight/last pull status per model tag: "pulling" or "failed: <reason>". */
  private final Map<String, String> pullStatus = new ConcurrentHashMap<>();

  private final ExecutorService pullExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public ModelDashboardService(
      OllamaAdminClient admin,
      ModelRuntimeService runtimes,
      PulledModelRepository pulledRepo,
      OllamaModelPuller puller) {
    this.admin = admin;
    this.runtimes = runtimes;
    this.pulledRepo = pulledRepo;
    this.puller = puller;
  }

  /** Builds the dashboard; degrades to DB-only rows when the runtime is unreachable. */
  @Transactional
  public RuntimeDashboard dashboard() {
    String baseUrl = runtimes.resolveOllamaBaseUrl();
    ModelRuntimeEntity runtime = runtimes.ensureRuntimeRow(baseUrl);
    Optional<DeployedContainer> managed = runtimes.findRunningOllama();

    boolean reachable = true;
    Map<String, OllamaAdminClient.LoadedModel> loaded = Map.of();
    try {
      List<OllamaAdminClient.LocalModel> local = admin.listLocal(baseUrl);
      loaded =
          admin.listLoaded(baseUrl).stream()
              .collect(Collectors.toMap(OllamaAdminClient.LoadedModel::name, Function.identity()));
      syncPulledModels(runtime.getId(), local);
    } catch (Exception e) {
      reachable = false;
      log.debug("[dashboard] runtime {} unreachable: {}", baseUrl, e.getMessage());
    }

    List<RuntimeModelView> views = new ArrayList<>();
    for (PulledModel m : pulledRepo.findByRuntimeId(runtime.getId())) {
      OllamaAdminClient.LoadedModel resident = loaded.get(m.getModelName());
      views.add(
          new RuntimeModelView(
              m.getModelName(),
              m.getSizeBytes() == null ? 0 : m.getSizeBytes(),
              m.getQuantization(),
              m.getDigest(),
              resident != null,
              resident != null ? resident.sizeVramBytes() : 0,
              resident != null ? resident.expiresAt() : null,
              m.getTemperature(),
              m.getNumCtx(),
              m.getKeepAlive()));
    }

    return new RuntimeDashboard(
        baseUrl,
        runtime.getGpuVendor() != null ? runtime.getGpuVendor().name() : null,
        reachable,
        managed.map(c -> c.getConfig().getName()).orElse(null),
        managed.map(c -> String.valueOf(c.getStatus())).orElse(null),
        views,
        Map.copyOf(pullStatus));
  }

  /** Load ("run") a model into memory using its saved keep-alive, resident by default. */
  public OllamaAdminClient.AdminResult load(String model) {
    String keepAlive =
        settingsFor(model)
            .map(ModelSettings::keepAlive)
            .filter(k -> k != null && !k.isBlank())
            .orElse("-1");
    return admin.load(runtimes.resolveOllamaBaseUrl(), model, keepAlive);
  }

  /** Unload ("pause") a model — frees RAM/VRAM immediately, blobs stay on disk. */
  public OllamaAdminClient.AdminResult unload(String model) {
    return admin.unload(runtimes.resolveOllamaBaseUrl(), model);
  }

  /** Delete a model's blobs from the runtime and forget its row + settings. */
  @Transactional
  public OllamaAdminClient.AdminResult deleteModel(String model) {
    String baseUrl = runtimes.resolveOllamaBaseUrl();
    OllamaAdminClient.AdminResult result = admin.delete(baseUrl, model);
    if (result.success()) {
      ModelRuntimeEntity runtime = runtimes.ensureRuntimeRow(baseUrl);
      pulledRepo.findByRuntimeIdAndModelName(runtime.getId(), model).ifPresent(pulledRepo::delete);
    }
    return result;
  }

  /** Save per-model settings; the row is created if the sync has not seen the model yet. */
  @Transactional
  public void updateSettings(String model, ModelSettings settings) {
    ModelRuntimeEntity runtime = runtimes.ensureRuntimeRow(runtimes.resolveOllamaBaseUrl());
    PulledModel row =
        pulledRepo
            .findByRuntimeIdAndModelName(runtime.getId(), model)
            .orElseGet(
                () -> {
                  PulledModel created = new PulledModel();
                  created.setId(UUID.randomUUID().toString());
                  created.setRuntimeId(runtime.getId());
                  created.setModelName(model);
                  return created;
                });
    row.setTemperature(settings.temperature());
    row.setNumCtx(settings.numCtx());
    row.setKeepAlive(settings.keepAlive());
    pulledRepo.save(row);
  }

  /** Saved settings for a model tag (used by ModelRouter on every chat). */
  public Optional<ModelSettings> settingsFor(String model) {
    return pulledRepo
        .findFirstByModelNameOrderByPulledAtDesc(model)
        .map(m -> new ModelSettings(m.getTemperature(), m.getNumCtx(), m.getKeepAlive()));
  }

  /** Fire-and-forget pull; the dashboard's {@code pulls} map reports progress state. */
  public void pullAsync(String modelTag) {
    String tag = modelTag.trim();
    if ("pulling".equals(pullStatus.get(tag))) return; // already in flight
    pullStatus.put(tag, "pulling");
    String baseUrl = runtimes.resolveOllamaBaseUrl();
    pullExecutor.submit(
        () -> {
          OllamaModelPuller.PullResult result = puller.pull(baseUrl, tag);
          if (result.success()) {
            pullStatus.remove(tag);
          } else {
            pullStatus.put(tag, "failed: " + result.message());
          }
        });
  }

  /** Mirrors the runtime's on-disk model list into {@code pulled_model}, preserving settings. */
  private void syncPulledModels(String runtimeId, List<OllamaAdminClient.LocalModel> local) {
    Set<String> seen = new HashSet<>();
    for (OllamaAdminClient.LocalModel lm : local) {
      seen.add(lm.name());
      PulledModel row =
          pulledRepo
              .findByRuntimeIdAndModelName(runtimeId, lm.name())
              .orElseGet(
                  () -> {
                    PulledModel created = new PulledModel();
                    created.setId(UUID.randomUUID().toString());
                    created.setRuntimeId(runtimeId);
                    created.setModelName(lm.name());
                    return created;
                  });
      row.setSizeBytes(lm.sizeBytes());
      row.setDigest(lm.digest());
      row.setQuantization(lm.quantization());
      pulledRepo.save(row);
    }
    // Models deleted outside Port Wrangler disappear from the dashboard too.
    for (PulledModel row : pulledRepo.findByRuntimeId(runtimeId)) {
      if (!seen.contains(row.getModelName())) {
        pulledRepo.delete(row);
      }
    }
  }
}
