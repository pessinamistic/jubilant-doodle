package com.dbdeployer.runtime;

import com.dbdeployer.deploy.DockerDeployEngine;
import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.model.ModelRuntimeEntity;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.ModelRuntimeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registry of managed LLM runtimes (roadmap §6 Phase 2). A runtime is any {@link DbType#OLLAMA}
 * instance deployed through the standard pipeline; {@code FinaliseStep} registers it here when the
 * container reaches RUNNING, and instance removal deregisters it.
 *
 * <p>{@link #resolveOllamaBaseUrl()} is the single lookup the model layer (agent {@code pullModel},
 * {@code ModelRouter}, the runtime dashboard) uses to find a live Ollama: the first RUNNING managed
 * instance wins, falling back to the configured {@code spring.ai.ollama.base-url} for
 * externally-run Ollama installs.
 */
@Slf4j
@Service
public class ModelRuntimeService {

  private final ModelRuntimeRepository runtimeRepo;
  private final DeployedContainerRepository containerRepo;
  private final DockerDeployEngine docker;
  private final String defaultBaseUrl;

  public ModelRuntimeService(
      ModelRuntimeRepository runtimeRepo,
      DeployedContainerRepository containerRepo,
      DockerDeployEngine docker,
      @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String defaultBaseUrl) {
    this.runtimeRepo = runtimeRepo;
    this.containerRepo = containerRepo;
    this.docker = docker;
    this.defaultBaseUrl = defaultBaseUrl;
  }

  /**
   * Registers (or refreshes) the {@code model_runtime} row for a deployment that reached RUNNING.
   * No-op for types that are not LLM runtimes, so the pipeline can call it unconditionally.
   */
  @Transactional
  public void registerIfModelRuntime(DeploymentConfig config, DeployedContainer container) {
    ModelRuntime runtime = runtimeFor(config.getDbType());
    if (runtime == null) return;

    ModelRuntimeEntity row =
        runtimeRepo
            .findByConfigId(config.getId())
            .orElseGet(
                () -> {
                  ModelRuntimeEntity created = new ModelRuntimeEntity();
                  created.setId(UUID.randomUUID().toString());
                  created.setConfigId(config.getId());
                  return created;
                });
    row.setRuntimeType(runtime);
    row.setBaseUrl(runtime.baseUrl(container.getHostPort()));
    row.setGpuVendor(docker.detectGpuVendor());
    runtimeRepo.save(row);
    log.info(
        "[runtime] Registered {} runtime '{}' at {} (gpu={})",
        runtime,
        config.getName(),
        row.getBaseUrl(),
        row.getGpuVendor());
  }

  /** Deregisters the runtime row when its backing instance is removed. No-op if none exists. */
  @Transactional
  public void removeForConfig(String configId) {
    runtimeRepo.deleteByConfigId(configId);
  }

  /**
   * The runtime row for a base URL, creating one on demand for runtimes Port Wrangler did not
   * deploy (a native/external Ollama reachable at the configured default URL). {@code configId}
   * stays null for those — there is no managed container behind them.
   */
  @Transactional
  public ModelRuntimeEntity ensureRuntimeRow(String baseUrl) {
    return runtimeRepo
        .findFirstByBaseUrl(baseUrl)
        .orElseGet(
            () -> {
              ModelRuntimeEntity row = new ModelRuntimeEntity();
              row.setId(UUID.randomUUID().toString());
              row.setRuntimeType(ModelRuntime.OLLAMA);
              row.setBaseUrl(baseUrl);
              row.setGpuVendor(docker.detectGpuVendor());
              return runtimeRepo.save(row);
            });
  }

  /** All registered runtime rows (for the dashboard). */
  public List<ModelRuntimeEntity> listRuntimes() {
    return runtimeRepo.findAll();
  }

  /**
   * Base URL of the first RUNNING managed Ollama instance, or the configured {@code
   * spring.ai.ollama.base-url} default when none is managed (e.g. a native Ollama install).
   */
  public String resolveOllamaBaseUrl() {
    return findRunningOllama()
        .map(c -> ModelRuntime.OLLAMA.baseUrl(c.getHostPort()))
        .orElse(defaultBaseUrl);
  }

  /** The first RUNNING managed Ollama instance, if any. */
  public Optional<DeployedContainer> findRunningOllama() {
    return containerRepo.findByStatus(InstanceStatus.RUNNING).stream()
        .filter(c -> c.getConfig() != null && c.getConfig().getDbType() == DbType.OLLAMA)
        .findFirst();
  }

  /** Maps a deployable type to its runtime kind; null for non-runtime types. */
  static ModelRuntime runtimeFor(DbType type) {
    return type == DbType.OLLAMA ? ModelRuntime.OLLAMA : null;
  }
}
