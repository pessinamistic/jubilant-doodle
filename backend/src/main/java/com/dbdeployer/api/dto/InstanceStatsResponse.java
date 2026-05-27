package com.dbdeployer.api.dto;

/**
 * Aggregate status counts returned by GET /api/instances/stats.
 *
 * @param total Active instances (all statuses except REMOVED and UNTRACKED)
 * @param running Containers currently running
 * @param restarting Containers in a restart loop
 * @param stopped Containers stopped but not removed
 * @param deploying Containers being deployed
 * @param removing Containers being removed
 * @param error Containers in ERROR state
 * @param removed Containers that have been removed (retained for history)
 * @param untracked Imported containers that have been untracked (container still alive)
 */
public record InstanceStatsResponse(
    int total,
    int running,
    int restarting,
    int stopped,
    int deploying,
    int removing,
    int error,
    int removed,
    int untracked) {}
