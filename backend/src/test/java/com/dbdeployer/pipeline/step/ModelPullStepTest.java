package com.dbdeployer.pipeline.step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.pipeline.model.StepType;
import com.dbdeployer.runtime.OllamaModelPuller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModelPullStepTest {

  @Mock private OllamaModelPuller puller;

  private DeploymentConfig config(String modelTag) {
    var c = new DeploymentConfig();
    c.setVersion(modelTag);
    return c;
  }

  private DeployedContainer container(int hostPort) {
    var c = new DeployedContainer();
    c.setHostPort(hostPort);
    return c;
  }

  @Test
  void type_is_pull_model() {
    assertThat(new ModelPullStep(puller).type()).isEqualTo(StepType.PULL_MODEL);
  }

  @Test
  void pulls_model_into_runtime_base_url_and_returns_message() throws Exception {
    when(puller.pull("http://localhost:11434", "llama3.1:8b"))
        .thenReturn(new OllamaModelPuller.PullResult(true, "Pulled model: llama3.1:8b"));

    String msg = new ModelPullStep(puller).execute(config("llama3.1:8b"), container(11434));

    assertThat(msg).isEqualTo("Pulled model: llama3.1:8b");
  }

  @Test
  void throws_step_execution_exception_on_pull_failure() {
    when(puller.pull("http://localhost:11434", "bad:model"))
        .thenReturn(new OllamaModelPuller.PullResult(false, "Pull failed for bad:model"));

    assertThatThrownBy(
            () -> new ModelPullStep(puller).execute(config("bad:model"), container(11434)))
        .isInstanceOf(StepExecutionException.class)
        .hasMessageContaining("Pull failed for bad:model");
  }
}
