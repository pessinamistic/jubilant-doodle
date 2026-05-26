package com.dbdeployer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for image status checks and background refresh schedules.
 */
@ConfigurationProperties(prefix = "dbdeployer.image-validation")
public class ImageValidationProperties {

    /** Timeout in milliseconds for Docker Hub API calls. */
    private int dockerHubTimeoutMs = 4000;

    /** Local image refresh interval in milliseconds. */
    private long localRefreshIntervalMs = 120_000;

    /** Docker Hub refresh interval in milliseconds. */
    private long dockerHubRefreshIntervalMs = 21_600_000;

    /** When true, background refresh jobs are enabled. */
    private boolean schedulerEnabled = true;

    public int getDockerHubTimeoutMs() { return dockerHubTimeoutMs; }
    public void setDockerHubTimeoutMs(int dockerHubTimeoutMs) { this.dockerHubTimeoutMs = dockerHubTimeoutMs; }

    public long getLocalRefreshIntervalMs() { return localRefreshIntervalMs; }
    public void setLocalRefreshIntervalMs(long localRefreshIntervalMs) { this.localRefreshIntervalMs = localRefreshIntervalMs; }

    public long getDockerHubRefreshIntervalMs() { return dockerHubRefreshIntervalMs; }
    public void setDockerHubRefreshIntervalMs(long dockerHubRefreshIntervalMs) { this.dockerHubRefreshIntervalMs = dockerHubRefreshIntervalMs; }

    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean schedulerEnabled) { this.schedulerEnabled = schedulerEnabled; }
}
