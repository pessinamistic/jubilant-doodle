package com.dbdeployer.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.checkerframework.common.aliasing.qual.Unique;

/**
 * Stable, user-facing configuration record for a database instance.
 *
 * <p>
 * Lives in the {@code deployment_config} table and is NEVER deleted — even
 * after the container is removed the row remains with the associated
 * {@link DeployedContainer} carrying status {@link InstanceStatus#REMOVED}.
 * This gives a full deployment history.
 */
@Setter
@Getter
@Entity
@ToString
@Table(name = "deployment_config")
public class DeploymentConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private DbType dbType;

    @NotBlank
    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "host_port", nullable = false)
    private int hostPort;

    @Column(name = "container_port", nullable = false)
    private int containerPort;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "database_name")
    private String databaseName;

    @Column(name = "extra_env_json", columnDefinition = "TEXT")
    private String extraEnvJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "deploy_method")
    private DeployMethod deployMethod;

    /**
     * True for the auto-provisioned system Postgres — cannot be stopped/removed by
     * users.
     */
    @Column(name = "is_system", nullable = false, columnDefinition = "boolean default false")
    private boolean isSystem = false;

    /**
     * True when this config was imported from a pre-existing container (not
     * deployed by DB Deployer). On remove: only untracks the container — does NOT
     * stop or delete it from Docker.
     */
    @Column(name = "is_imported", nullable = false, columnDefinition = "boolean default false")
    private boolean isImported = false;

    @Unique
    @Column(name = "template_id")
    private String templateId;

    /**
     * When true this row is a reusable configuration blueprint, not a live
     * deployment.
     */
    @Column(name = "is_template", nullable = false)
    private boolean isTemplate = false;

    /**
     * Human-readable description (populated for templates, null for plain
     * instances).
     */
    @Column(name = "description")
    private String description;

    /**
     * Number of instances launched from this template row. Always 0 for
     * non-template rows.
     */
    @Column(name = "deploy_count", nullable = false)
    private int deployCount = 0;

    /**
     * The current (or last) deployment state for this config. Cascade ALL so
     * saving/deleting the config cascades to the container record.
     */

    @OneToOne(mappedBy = "config", cascade = CascadeType.ALL, fetch = FetchType.EAGER, optional = true)
    private DeployedContainer container;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<DeployedContainer> containers;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
