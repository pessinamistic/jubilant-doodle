package com.dbdeployer.api.dto;

import java.util.List;

/**
 * Deployment frequency + instance breakdown for chart rendering. Returned by
 * {@code GET
 * /api/system/metrics/activity}.
 */
public record DeploymentActivityResponse(
        List<DayCount> deploymentsByDay, List<LabelCount> instancesByDbType, List<LabelCount> instancesByStatus) {

    /** Deployments on a given date (ISO yyyy-MM-dd). */
    public record DayCount(String date, long count) {}

    /** Count of instances sharing a label (db type or status string). */
    public record LabelCount(String label, long count) {}
}
