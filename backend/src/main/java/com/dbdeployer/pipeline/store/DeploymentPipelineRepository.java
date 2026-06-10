package com.dbdeployer.pipeline.store;

import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentPipelineRepository extends JpaRepository<DeploymentPipeline, String> {

  Optional<DeploymentPipeline> findTopByConfigIdOrderByCreatedAtDesc(String configId);

  List<DeploymentPipeline> findByStatus(PipelineStatus status);
}
