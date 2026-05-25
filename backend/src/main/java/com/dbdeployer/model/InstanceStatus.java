package com.dbdeployer.model;

public enum InstanceStatus {
    DEPLOYING,
    RUNNING,
    STOPPED,
    ERROR,
    REMOVING,
    /** Container has been stopped and deleted from Docker. Config record is retained for history. */
    REMOVED
}
