package com.dbdeployer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Stores source-aware image availability checks for a catalog tool image tag.
 */
@Entity
@Table(
        name = "image_tracking_status",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_image_tracking_target",
                        columnNames = {"db_type", "image_name", "image_tag"}))
public class ImageTrackingStatus {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private DbType dbType;

    @Column(name = "image_name", nullable = false)
    private String imageName;

    @Column(name = "image_tag", nullable = false)
    private String imageTag;

    @Column(name = "docker_hub_managed", nullable = false)
    private boolean dockerHubManaged;

    @Enumerated(EnumType.STRING)
    @Column(name = "local_status", nullable = false, columnDefinition = "VARCHAR(32)")
    private ImageAvailabilityState localStatus = ImageAvailabilityState.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "docker_hub_status", nullable = false, columnDefinition = "VARCHAR(32)")
    private ImageAvailabilityState dockerHubStatus = ImageAvailabilityState.NOT_APPLICABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, columnDefinition = "VARCHAR(32)")
    private ImageValidationDecision decision = ImageValidationDecision.ALLOW_WITH_WARNING;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "local_checked_at")
    private LocalDateTime localCheckedAt;

    @Column(name = "docker_hub_checked_at")
    private LocalDateTime dockerHubCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getImageTag() {
        return imageTag;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    public boolean isDockerHubManaged() {
        return dockerHubManaged;
    }

    public void setDockerHubManaged(boolean dockerHubManaged) {
        this.dockerHubManaged = dockerHubManaged;
    }

    public ImageAvailabilityState getLocalStatus() {
        return localStatus;
    }

    public void setLocalStatus(ImageAvailabilityState localStatus) {
        this.localStatus = localStatus;
    }

    public ImageAvailabilityState getDockerHubStatus() {
        return dockerHubStatus;
    }

    public void setDockerHubStatus(ImageAvailabilityState dockerHubStatus) {
        this.dockerHubStatus = dockerHubStatus;
    }

    public ImageValidationDecision getDecision() {
        return decision;
    }

    public void setDecision(ImageValidationDecision decision) {
        this.decision = decision;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getLocalCheckedAt() {
        return localCheckedAt;
    }

    public void setLocalCheckedAt(LocalDateTime localCheckedAt) {
        this.localCheckedAt = localCheckedAt;
    }

    public LocalDateTime getDockerHubCheckedAt() {
        return dockerHubCheckedAt;
    }

    public void setDockerHubCheckedAt(LocalDateTime dockerHubCheckedAt) {
        this.dockerHubCheckedAt = dockerHubCheckedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
