package com.dbdeployer.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.pipeline.model.DeployErrorCode;
import com.dbdeployer.pipeline.model.DeploymentPipeline;
import com.dbdeployer.pipeline.model.PipelineStatus;
import com.dbdeployer.pipeline.model.PipelineStep;
import com.dbdeployer.pipeline.model.StepStatus;
import com.dbdeployer.pipeline.model.StepType;
import com.dbdeployer.pipeline.step.DeployStep;
import com.dbdeployer.pipeline.step.StepExecutionException;
import com.dbdeployer.pipeline.store.DeploymentPipelineRepository;
import com.dbdeployer.pipeline.store.PipelineStepRepository;
import com.dbdeployer.store.DeployedContainerRepository;
import com.dbdeployer.store.DeploymentConfigRepository;

/**
 * Executes a deploy pipeline asynchronously, step by step.
 *
 * <p>Re-fetches all entities from the DB by ID before doing any work so that
 * the caller's transaction is guaranteed to have committed first.</p>
 */
@Service
public class PipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunner.class);

    private final DeploymentPipelineRepository pipelineRepo;
    private final PipelineStepRepository       stepRepo;
    private final DeploymentConfigRepository   configRepo;
    private final DeployedContainerRepository  containerRepo;
    private final Map<StepType, DeployStep>    stepRegistry;
    private final PipelineProperties           props;

    public PipelineRunner(DeploymentPipelineRepository pipelineRepo,
                          PipelineStepRepository stepRepo,
                          DeploymentConfigRepository configRepo,
                          DeployedContainerRepository containerRepo,
                          List<DeployStep> steps,
                          PipelineProperties props) {
        this.pipelineRepo  = pipelineRepo;
        this.stepRepo      = stepRepo;
        this.configRepo    = configRepo;
        this.containerRepo = containerRepo;
        this.props         = props;
        this.stepRegistry  = steps.stream()
                .collect(Collectors.toMap(DeployStep::type, Function.identity()));
    }

    @Async
    public void run(String pipelineId) {
        log.info("[runner] Starting pipeline {}", pipelineId);

        // ── Re-fetch everything fresh (post-TX) ──
        DeploymentPipeline pipeline = pipelineRepo.findById(pipelineId)
                .orElseThrow(() -> new IllegalStateException("Pipeline not found: " + pipelineId));

        DeploymentConfig config = configRepo.findById(pipeline.getConfigId())
                .orElseThrow(() -> new IllegalStateException(
                        "Config not found for pipeline: " + pipelineId));

        DeployedContainer container = containerRepo.findByConfigId(config.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Container record not found for config: " + config.getId()));

        List<PipelineStep> steps = stepRepo.findByPipelineIdOrderByStepOrderAsc(pipelineId);

        // ── Mark pipeline RUNNING ──
        pipeline.setStatus(PipelineStatus.RUNNING);
        pipeline.setStartedAt(Instant.now());
        pipelineRepo.save(pipeline);

        boolean failed = false;

        for (PipelineStep step : steps) {
            if (failed) {
                step.setStatus(StepStatus.SKIPPED);
                stepRepo.save(step);
                continue;
            }

            // ── Step: RUNNING ──
            step.setStatus(StepStatus.RUNNING);
            step.setStartedAt(Instant.now());
            stepRepo.save(step);

            // ── Inter-step delay (cosmetic) ──
            if (step.getStepOrder() > 0 && props.getStepDelayMs() > 0) {
                try { Thread.sleep(props.getStepDelayMs()); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }

            DeployStep impl = stepRegistry.get(step.getStepType());
            if (impl == null) {
                log.error("[runner] No impl registered for step type {}", step.getStepType());
                markStepFailed(step, DeployErrorCode.UNEXPECTED_ERROR,
                        "No handler registered for step: " + step.getStepType());
                failed = true;
                continue;
            }

            try {
                String msg = impl.execute(config, container);
                step.setStatus(StepStatus.SUCCESS);
                step.setMessage(msg);
                step.setCompletedAt(Instant.now());
                stepRepo.save(step);
                // Persist any container mutations made by this step
                containerRepo.save(container);
                log.info("[runner] Step {} SUCCESS: {}", step.getStepType(), msg);
            } catch (StepExecutionException e) {
                log.error("[runner] Step {} FAILED [{}]: {}",
                        step.getStepType(), e.getErrorCode(), e.getMessage(), e);
                markStepFailed(step, e.getErrorCode(), e.getMessage());
                pipeline.setErrorCode(e.getErrorCode());
                pipeline.setErrorMessage(e.getMessage());
                failed = true;
            } catch (Exception e) {
                log.error("[runner] Step {} unexpected error: {}", step.getStepType(), e.getMessage(), e);
                markStepFailed(step, DeployErrorCode.UNEXPECTED_ERROR, e.getMessage());
                pipeline.setErrorCode(DeployErrorCode.UNEXPECTED_ERROR);
                pipeline.setErrorMessage(e.getMessage());
                failed = true;
            }
        }

        // ── Finalise pipeline ──
        pipeline.setStatus(failed ? PipelineStatus.FAILED : PipelineStatus.SUCCESS);
        pipeline.setCompletedAt(Instant.now());
        pipelineRepo.save(pipeline);

        if (failed) {
            // Mark container ERROR if the deploy failed before the container was started
            if (container.getStatus() == InstanceStatus.DEPLOYING) {
                container.setStatus(InstanceStatus.ERROR);
                containerRepo.save(container);
            }
            log.error("[runner] Pipeline {} FAILED — error: {} — {}",
                    pipelineId, pipeline.getErrorCode(), pipeline.getErrorMessage());
        } else {
            log.info("[runner] Pipeline {} SUCCESS — container {}",
                    pipelineId, container.getContainerId() != null
                            ? container.getContainerId().substring(0, 12) : "?");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void markStepFailed(PipelineStep step, DeployErrorCode code, String message) {
        step.setStatus(StepStatus.FAILED);
        step.setMessage("[" + code + "] " + message);
        step.setCompletedAt(Instant.now());
        stepRepo.save(step);
    }
}
