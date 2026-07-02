package com.dbdeployer.ai;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbdeployer.event.InstanceDeployedEvent;
import com.dbdeployer.event.InstanceRemovedEvent;
import com.dbdeployer.model.DeployedContainer;
import com.dbdeployer.service.DbInstanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RagIngestionTriggerTest {

  @Mock private IngestionService ingestionService;
  @Mock private DbInstanceService instanceService;

  private RagIngestionTrigger trigger(boolean scheduled) {
    return new RagIngestionTrigger(ingestionService, instanceService, scheduled);
  }

  @Test
  void deploy_event_reindexes_the_instance_with_its_logs() throws Exception {
    DeployedContainer container = new DeployedContainer();
    container.setId("inst-1");
    when(instanceService.getById("inst-1")).thenReturn(container);
    when(instanceService.getLogs("inst-1", 500)).thenReturn("log line");

    trigger(true).onInstanceDeployed(new InstanceDeployedEvent("inst-1"));

    verify(ingestionService).reindexInstance(container, "log line");
  }

  @Test
  void deploy_event_survives_ingestion_failures() {
    when(instanceService.getById("inst-1")).thenThrow(new IllegalArgumentException("gone"));

    trigger(true).onInstanceDeployed(new InstanceDeployedEvent("inst-1")); // must not throw
  }

  @Test
  void removal_event_drops_the_instance_documents() {
    trigger(true).onInstanceRemoved(new InstanceRemovedEvent("inst-9"));

    verify(ingestionService).deleteForInstance("inst-9");
  }

  @Test
  void scheduled_reindex_respects_the_flag() {
    trigger(false).scheduledReindex();
    verify(ingestionService, never()).ingestAllInstances();

    trigger(true).scheduledReindex();
    verify(ingestionService).ingestAllInstances();
  }
}
