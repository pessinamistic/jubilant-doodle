package com.dbdeployer.model;

public enum InstanceStatus {
    DEPLOYING,
    RUNNING,
    RESTARTING,
    STOPPED,
    ERROR,
    REMOVING,
    /** Container has been stopped and deleted from Docker. Config record is retained for history. */
    REMOVED,
    /**
     * Imported container that has been untracked — the underlying Docker container is still
     * alive but Port Wrangler is no longer managing it. Can be re-tracked at any time.
     */
    UNTRACKED
}
