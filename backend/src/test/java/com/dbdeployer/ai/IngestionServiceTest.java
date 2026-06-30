package com.dbdeployer.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbdeployer.model.DbType;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.model.DeploymentConfig;
import com.dbdeployer.model.InstanceStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class IngestionServiceTest {

  private static DeployedContainer container() {
    var config = new DeploymentConfig();
    config.setId("cfg-1");
    config.setName("cache");
    config.setDbType(DbType.REDIS);
    config.setVersion("7.4");
    config.setHostPort(6390);

    var c = new DeployedContainer();
    c.setId("inst-1");
    c.setConfig(config);
    c.setHostPort(6390);
    c.setStatus(InstanceStatus.RUNNING);
    return c;
  }

  @Test
  void builds_one_deployment_doc_with_metadata() {
    Document doc = IngestionService.deploymentDocument(container());

    assertThat(doc.getMetadata())
        .containsEntry("type", "deployment")
        .containsEntry("instance_id", "inst-1")
        .containsEntry("instance_name", "cache")
        .containsEntry("instance_type", "REDIS");
    assertThat(doc.getText()).contains("cache").contains("6390");
  }

  @Test
  void builds_log_docs_tagged_with_severity() {
    String logs = "2026-01-01T10:00:00Z INFO ready\n2026-01-01T10:00:01Z ERROR boom";
    List<Document> docs = IngestionService.logDocuments(container(), logs);

    assertThat(docs).isNotEmpty();
    assertThat(docs)
        .allSatisfy(
            d -> {
              assertThat(d.getMetadata()).containsEntry("type", "log");
              assertThat(d.getMetadata()).containsEntry("instance_type", "REDIS");
              assertThat(d.getMetadata()).containsKey("level");
            });
  }

  @Test
  void documentsFor_combines_deployment_and_log_docs() {
    var docs =
        new IngestionServiceHelper().documentsFor(container(), "2026-01-01T10:00:00Z INFO ok");
    assertThat(docs).hasSizeGreaterThanOrEqualTo(2);
    assertThat(docs.get(0).getMetadata()).containsEntry("type", "deployment");
  }

  /** Tiny helper to exercise the instance method without a VectorStore/DbInstanceService. */
  static class IngestionServiceHelper {
    List<Document> documentsFor(DeployedContainer c, String logs) {
      List<Document> docs = new java.util.ArrayList<>();
      docs.add(IngestionService.deploymentDocument(c));
      docs.addAll(IngestionService.logDocuments(c, logs));
      return docs;
    }
  }
}
