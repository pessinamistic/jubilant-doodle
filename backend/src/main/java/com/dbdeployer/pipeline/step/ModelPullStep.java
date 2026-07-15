package com.dbdeployer.pipeline.step;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.StepType;
import com.dbdeployer.runtime.ModelRuntime;
import com.dbdeployer.runtime.OllamaModelPuller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pulls a model into a deployed LLM runtime, mirroring how {@link ImagePullStep} pulls a Docker
 * image. Reuses the {@link DeployStep} SPI so model-pull work runs through the same DB-persisted,
 * async pipeline as container deploys.
 *
 * <p>Interpretation of the shared step contract: the model tag is carried in {@link
 * DeploymentConfig#getVersion()} and the runtime is reached at {@code http://localhost:<hostPort>}
 * (the published Ollama port on {@link DeployedContainer}).
 */
@Slf4j
@Component
public class ModelPullStep implements DeployStep {

  private final OllamaModelPuller puller;

  public ModelPullStep(OllamaModelPuller puller) {
    this.puller = puller;
  }

  @Override
  public StepType type() {
    return StepType.PULL_MODEL;
  }

  @Override
  public String execute(DeploymentConfig config, DeployedContainer container)
      throws StepExecutionException {
    String modelTag = config.getVersion();
    String baseUrl = ModelRuntime.OLLAMA.baseUrl(container.getHostPort());

    OllamaModelPuller.PullResult result = puller.pull(baseUrl, modelTag);
    if (!result.success()) {
      throw new StepExecutionException(DeployErrorCode.MODEL_PULL_FAILED, result.message());
    }
    return result.message();
  }
}
