package com.dbdeployer.ai;

import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import com.dbdeployer.service.DbInstanceService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Indexes Port Wrangler's own state into the shared pgvector {@code vector_store} for RAG grounding
 * (roadmap Phase 4): one document per deployment instance, plus severity-tagged chunks of each
 * instance's container logs. Documents carry a {@code type} discriminator and filterable metadata
 * ({@code instance_type}, {@code level}, ...).
 *
 * <p>The document-building methods are pure and unit-testable; {@link #ingestInstance} is the thin
 * adapter that writes them to the {@link VectorStore}.
 */
@Slf4j
@Service
public class IngestionService {

  static final int LOG_RECORDS_PER_CHUNK = 10;

  private final VectorStore vectorStore;
  private final DbInstanceService instanceService;

  public IngestionService(VectorStore vectorStore, DbInstanceService instanceService) {
    this.vectorStore = vectorStore;
    this.instanceService = instanceService;
  }

  /** Re-index every active instance (deployment record + recent logs). */
  public void ingestAllInstances() {
    for (DeployedContainer container : instanceService.listAll()) {
      if (container.getStatus() == InstanceStatus.REMOVED) continue;
      String logs = null;
      try {
        logs = instanceService.getLogs(container.getId(), 500);
      } catch (Exception e) {
        log.debug("[rag] logs unavailable for {}: {}", container.getId(), e.getMessage());
      }
      ingestInstance(container, logs);
    }
  }

  /** Write the deployment doc + log-chunk docs for one instance to the vector store. */
  public void ingestInstance(DeployedContainer container, String logs) {
    List<Document> docs = documentsFor(container, logs);
    if (!docs.isEmpty()) {
      vectorStore.add(docs);
      log.info("[rag] ingested {} document(s) for instance {}", docs.size(), container.getId());
    }
  }

  /** Pure: builds the deployment document + one document per log chunk. */
  public List<Document> documentsFor(DeployedContainer container, String logs) {
    List<Document> docs = new ArrayList<>();
    docs.add(deploymentDocument(container));
    docs.addAll(logDocuments(container, logs));
    return docs;
  }

  static Document deploymentDocument(DeployedContainer container) {
    DeploymentConfig config = container.getConfig();
    String content =
        ("Deployment '%s' — type=%s version=%s status=%s hostPort=%d. "
                + "Connect on localhost:%d.")
            .formatted(
                config.getName(),
                config.getDbType(),
                config.getVersion(),
                container.getStatus(),
                container.getHostPort(),
                container.getHostPort());

    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("type", "deployment");
    meta.put("instance_id", container.getId());
    meta.put("instance_name", config.getName());
    meta.put("instance_type", config.getDbType().name());
    meta.put("status", String.valueOf(container.getStatus()));
    return Document.builder().text(content).metadata(meta).build();
  }

  static List<Document> logDocuments(DeployedContainer container, String logs) {
    List<Document> docs = new ArrayList<>();
    DeploymentConfig config = container.getConfig();
    for (LogChunker.LogChunk chunk : LogChunker.chunk(logs, LOG_RECORDS_PER_CHUNK)) {
      Map<String, Object> meta = new LinkedHashMap<>();
      meta.put("type", "log");
      meta.put("instance_id", container.getId());
      meta.put("instance_name", config.getName());
      meta.put("instance_type", config.getDbType().name());
      meta.put("level", chunk.level());
      docs.add(Document.builder().text(chunk.content()).metadata(meta).build());
    }
    return docs;
  }
}
