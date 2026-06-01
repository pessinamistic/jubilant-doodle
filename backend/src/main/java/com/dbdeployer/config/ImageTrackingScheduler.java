package com.dbdeployer.config;

import com.dbdeployer.service.ImageValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic local and Docker Hub image tracking refresh jobs. */
@Component
public class ImageTrackingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImageTrackingScheduler.class);

    private final ImageValidationService imageValidationService;
    private final ImageValidationProperties properties;

    public ImageTrackingScheduler(ImageValidationService imageValidationService, ImageValidationProperties properties) {
        this.imageValidationService = imageValidationService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${dbdeployer.image-validation.local-refresh-interval-ms:120000}",
            initialDelayString = "${dbdeployer.image-validation.local-refresh-initial-delay-ms:10000}")
    public void refreshLocal() {
        if (!properties.isSchedulerEnabled()) return;
        try {
            int count = imageValidationService.refreshLocalStatuses();
            log.debug("Refreshed local image statuses for {} tags", count);
        } catch (Exception e) {
            log.warn("Local image refresh failed: {}", e.getMessage());
        }
    }

    @Scheduled(
            fixedDelayString = "${dbdeployer.image-validation.docker-hub-refresh-interval-ms:21600000}",
            initialDelayString = "${dbdeployer.image-validation.docker-hub-refresh-initial-delay-ms:30000}")
    public void refreshDockerHub() {
        if (!properties.isSchedulerEnabled()) return;
        try {
            int count = imageValidationService.refreshDockerHubStatuses();
            log.debug("Refreshed Docker Hub image statuses for {} tags", count);
        } catch (Exception e) {
            log.warn("Docker Hub image refresh failed: {}", e.getMessage());
        }
    }
}
