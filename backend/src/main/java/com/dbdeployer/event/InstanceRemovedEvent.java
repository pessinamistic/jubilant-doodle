package com.dbdeployer.event;

/** Published when an instance is removed (container gone, row kept as REMOVED). */
public record InstanceRemovedEvent(String containerRecordId) {}
