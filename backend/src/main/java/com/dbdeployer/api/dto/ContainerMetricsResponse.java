package com.dbdeployer.api.dto;

/**
 * Live container metrics snapshot — combines Docker stats + inspect data plus a port reachability
 * probe.
 *
 * <p>All byte values are raw bytes. The frontend converts to human-readable units. If the container
 * is not running, {@code available} will be {@code false}.
 */
public record ContainerMetricsResponse(

    /** false when the container is not running or Docker is unreachable */
    boolean available,

    // ── CPU ──────────────────────────────────────────────────────────────
    /** CPU utilisation 0-100 (across all cores) */
    double cpuPercent,
    /** Number of CPU cores visible to the container */
    int cpuCores,

    // ── Memory ───────────────────────────────────────────────────────────
    /** Resident memory bytes (usage - cache on Linux) */
    long memUsageBytes,
    /** Container memory limit in bytes */
    long memLimitBytes,
    /** mem usage percentage 0-100 */
    double memPercent,

    // ── Network I/O (cumulative since container start) ───────────────────
    long netRxBytes,
    long netTxBytes,
    long netRxPackets,
    long netTxPackets,

    // ── Block I/O (cumulative) ───────────────────────────────────────────
    long blockReadBytes,
    long blockWriteBytes,

    // ── Process info ─────────────────────────────────────────────────────
    long pids,

    // ── Container health ─────────────────────────────────────────────────
    int restartCount,
    /** Full image name including tag, e.g. "mongo:8.0" */
    String image,
    /** Docker container state string: "running", "exited", etc. */
    String containerState,

    // ── Port probe ───────────────────────────────────────────────────────
    /** Whether the DB port was reachable at the time of this snapshot */
    boolean portReachable,
    /** TCP connect latency in milliseconds, or -1 if not reachable */
    long portLatencyMs) {
  /** Sentinel returned when the container is stopped / unavailable. */
  public static ContainerMetricsResponse unavailable() {
    return new ContainerMetricsResponse(
        false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, "stopped", false, -1);
  }
}
