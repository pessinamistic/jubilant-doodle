package com.dbdeployer.pipeline.store;

import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PipelineStepRepository extends JpaRepository<PipelineStep, String> {

    List<PipelineStep> findByPipelineIdOrderByStepOrderAsc(String pipelineId);

    List<PipelineStep> findByPipelineIdAndStatus(String pipelineId, StepStatus status);
}
