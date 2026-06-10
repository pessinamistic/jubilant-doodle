package com.dbdeployer.config;

import com.dbdeployer.service.DbInstanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically syncs container statuses from Docker every 30 seconds */
@Component
public class StatusSyncScheduler {

  private final DbInstanceService service;

  public StatusSyncScheduler(
    DbInstanceService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 30_000)
  public void sync() {
    service.syncStatuses();
  }
}
