package com.dbdeployer.pipeline.store;

import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineStepRepository extends JpaRepository<PipelineStep, String> {

    List<PipelineStep> findByPipelineIdOrderByStepOrderAsc(String pipelineId);

    List<PipelineStep> findByPipelineIdAndStatus(String pipelineId, StepStatus status);
}
