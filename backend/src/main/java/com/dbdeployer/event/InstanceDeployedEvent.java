package com.dbdeployer.event;

/** Published when a deploy pipeline finalises with a RUNNING container. */
public record InstanceDeployedEvent(String containerRecordId) {}
