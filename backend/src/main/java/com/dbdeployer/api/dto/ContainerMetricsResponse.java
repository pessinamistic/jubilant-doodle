package com.dbdeployer.api.dto;

import java.util.Map;

/**
 * Live container metrics snapshot — Docker stats + inspect + a port reachability
 * probe + optional tool-specific telemetry (e.g. Postgres connection count).
 *
 * <p>
 * All byte values are raw bytes. The frontend converts to human-readable units.
 * If the container is not running, {@code available} will be {@code false}.
 */
public record ContainerMetricsResponse(

        /** false when the container is not running or Docker is unreachable */
        boolean available,

        // ── CPU ──────────────────────────────────────────────────────────────
        /** CPU utilisation 0-100 (across all cores) */
        double cpuPercent,
        /** Number of CPU cores visible to the container */
        int cpuCores,
        /** Approx % of CPU time the kernel throttled this container (0-100). 0 when unavailable. */
        double cpuThrottledPercent,

        // ── Memory ───────────────────────────────────────────────────────────
        /** Resident memory bytes (usage - cache on Linux) */
        long memUsageBytes,
        /** Peak memory usage observed by the kernel (0 when unavailable) */
        long memMaxUsageBytes,
        /** Container memory limit in bytes */
        long memLimitBytes,
        /** mem usage percentage 0-100 */
        double memPercent,

        // ── Network I/O (cumulative since container start) ───────────────────
        long netRxBytes,
        long netTxBytes,
        long netRxPackets,
        long netTxPackets,
        /** Cumulative receive errors across all interfaces */
        long netRxErrors,
        /** Cumulative transmit errors across all interfaces */
        long netTxErrors,

        // ── Block I/O (cumulative) ───────────────────────────────────────────
        long blockReadBytes,
        long blockWriteBytes,
        /** Cumulative read operation count, when the kernel exposes it */
        long blockReadOps,
        /** Cumulative write operation count */
        long blockWriteOps,

        // ── Process info ─────────────────────────────────────────────────────
        long pids,
        /** Container PID limit when set (0 when unlimited / unknown) */
        long pidsLimit,

        // ── Container health ─────────────────────────────────────────────────
        int restartCount,
        /** Full image name including tag, e.g. "mongo:8.0" */
        String image,
        /** Docker container state string: "running", "exited", etc. */
        String containerState,
        /** Docker healthcheck status: "healthy" / "unhealthy" / "starting" / "none" */
        String healthStatus,
        /** True when the last termination was an OOM kill */
        boolean oomKilled,
        /** Container start time ISO-8601 (null when never started) */
        String startedAt,
        /** Seconds since the container last started (0 when not running) */
        long uptimeSeconds,

        // ── Port probe ───────────────────────────────────────────────────────
        /** Whether the DB port was reachable at the time of this snapshot */
        boolean portReachable,
        /** TCP connect latency in milliseconds, or -1 if not reachable */
        long portLatencyMs,

        // ── Tool-specific telemetry ─────────────────────────────────────────
        /** Tool-specific metrics (Postgres connections, Redis ops/sec, etc.). Never null. */
        Map<String, Object> toolMetrics) {

    /** Sentinel returned when the container is stopped / unavailable. */
    public static ContainerMetricsResponse unavailable() {
        return new ContainerMetricsResponse(
                false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, "stopped", "none", false, null,
                0, false, -1, Map.of());
    }

    /** Returns a copy of this response with {@code toolMetrics} replaced. */
    public ContainerMetricsResponse withToolMetrics(Map<String, Object> tools) {
        return new ContainerMetricsResponse(
                available,
                cpuPercent,
                cpuCores,
                cpuThrottledPercent,
                memUsageBytes,
                memMaxUsageBytes,
                memLimitBytes,
                memPercent,
                netRxBytes,
                netTxBytes,
                netRxPackets,
                netTxPackets,
                netRxErrors,
                netTxErrors,
                blockReadBytes,
                blockWriteBytes,
                blockReadOps,
                blockWriteOps,
                pids,
                pidsLimit,
                restartCount,
                image,
                containerState,
                healthStatus,
                oomKilled,
                startedAt,
                uptimeSeconds,
                portReachable,
                portLatencyMs,
                tools == null ? Map.of() : tools);
    }
}
