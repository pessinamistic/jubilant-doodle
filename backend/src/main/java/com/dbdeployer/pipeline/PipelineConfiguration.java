package com.dbdeployer.pipeline;

import com.dbdeployer.pipeline.step.ContainerCreateStep;
import com.dbdeployer.pipeline.step.ContainerStartStep;
import com.dbdeployer.pipeline.step.DeployStep;
import com.dbdeployer.pipeline.step.FinaliseStep;
import com.dbdeployer.pipeline.step.ImagePullStep;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PipelineConfiguration {

  @Bean
  public List<DeployStep> pipelineHandlers(
      ImagePullStep imagePullStep,
      ContainerCreateStep containerCreateStep,
      ContainerStartStep containerStartStep,
      FinaliseStep finaliseStep) {
    return List.of(imagePullStep, containerCreateStep, containerStartStep, finaliseStep);
  }
}
