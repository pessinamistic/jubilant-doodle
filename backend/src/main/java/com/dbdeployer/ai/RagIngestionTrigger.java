package com.dbdeployer.ai;

import com.dbdeployer.event.InstanceDeployedEvent;
import com.dbdeployer.event.InstanceRemovedEvent;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.service.DbInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the RAG knowledge base current (roadmap 6e): re-indexes an instance when its deploy
 * finalises, drops its documents when it is removed, and re-indexes everything on a schedule so
 * container-log chunks stay fresh. Event-driven (not direct calls from the pipeline) so the deploy
 * path never gains a dependency on the AI layer; every hook is best-effort.
 */
@Slf4j
@Component
public class RagIngestionTrigger {

  private final IngestionService ingestionService;
  private final DbInstanceService instanceService;
  private final boolean scheduledReindexEnabled;

  public RagIngestionTrigger(
      IngestionService ingestionService,
      DbInstanceService instanceService,
      @Value("${portwrangler.rag.scheduled-reindex:true}") boolean scheduledReindexEnabled) {
    this.ingestionService = ingestionService;
    this.instanceService = instanceService;
    this.scheduledReindexEnabled = scheduledReindexEnabled;
  }

  @Async
  @EventListener
  public void onInstanceDeployed(InstanceDeployedEvent event) {
    try {
      DeployedContainer container = instanceService.getById(event.containerRecordId());
      String logs = null;
      try {
        logs = instanceService.getLogs(container.getId(), 500);
      } catch (Exception e) {
        log.debug("[rag] logs unavailable for {}: {}", container.getId(), e.getMessage());
      }
      ingestionService.reindexInstance(container, logs);
    } catch (Exception e) {
      log.debug("[rag] deploy ingestion skipped: {}", e.getMessage());
    }
  }

  @Async
  @EventListener
  public void onInstanceRemoved(InstanceRemovedEvent event) {
    try {
      ingestionService.deleteForInstance(event.containerRecordId());
    } catch (Exception e) {
      log.debug("[rag] removal cleanup skipped: {}", e.getMessage());
    }
  }

  /** Periodic full refresh so log chunks don't go stale between lifecycle events. */
  @Scheduled(
      fixedDelayString = "${portwrangler.rag.reindex-interval-ms:1800000}",
      initialDelayString = "${portwrangler.rag.reindex-initial-delay-ms:300000}")
  public void scheduledReindex() {
    if (!scheduledReindexEnabled) return;
    try {
      ingestionService.ingestAllInstances();
    } catch (Exception e) {
      log.debug("[rag] scheduled reindex skipped: {}", e.getMessage());
    }
  }
}
