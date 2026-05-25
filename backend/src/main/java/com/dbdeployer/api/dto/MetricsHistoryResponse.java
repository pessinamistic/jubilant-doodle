package com.dbdeployer.api.dto;

import java.util.List;

/**
 * Rolling time-series of JVM + connection-pool samples.
 * Returned by {@code GET /api/system/metrics/history}.
 */
public record MetricsHistoryResponse(
        List<MetricSample> samples,
        int                windowSeconds
) {

    public record MetricSample(
            String timestamp,    // ISO-8601
            long   heapUsedMb,
            long   heapMaxMb,
            int    heapPct,
            int    poolActive,
            int    poolMax,
            int    poolPct
    ) {}
}
