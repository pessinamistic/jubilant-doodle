--liquibase formatted sql
--changeset portWrangler:1 labels:init comment:Initial schema for Port Wrangler system database

-- ── deployment_config ────────────────────────────────────────────────────────
-- Stable, user-facing configuration record for a deployed database instance.
-- Rows are never deleted: REMOVED state is recorded on the container side.
CREATE TABLE deployment_config (
    id               VARCHAR(36)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    db_type          VARCHAR(50)  NOT NULL,
    version          VARCHAR(255) NOT NULL,
    host_port        INT          NOT NULL,
    container_port   INT          NOT NULL,
    username         VARCHAR(255),
    password         VARCHAR(255),
    database_name    VARCHAR(255),
    extra_env_json   TEXT,
    deploy_method    VARCHAR(50)  NOT NULL,
    is_system        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_imported      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    CONSTRAINT pk_deployment_config PRIMARY KEY (id),
    CONSTRAINT uq_deployment_config_name UNIQUE (name)
);

-- ── deployed_container ───────────────────────────────────────────────────────
-- Docker runtime state, one row per active (or historical) deployment.
-- Kept with status=REMOVED on container removal so history is never lost.
CREATE TABLE deployed_container (
    id                  VARCHAR(36)   NOT NULL,
    config_id           VARCHAR(36)   NOT NULL,
    container_id        VARCHAR(255),
    container_name      VARCHAR(255),
    status              VARCHAR(50)   NOT NULL,
    data_directory      VARCHAR(1000),
    started_at          TIMESTAMPTZ,
    removed_at          TIMESTAMPTZ,
    latest_pipeline_id  VARCHAR(36),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ,
    CONSTRAINT pk_deployed_container PRIMARY KEY (id),
    CONSTRAINT fk_deployed_container_config
        FOREIGN KEY (config_id) REFERENCES deployment_config (id)
);

-- ── deployment_pipeline ──────────────────────────────────────────────────────
-- One row per deploy attempt.  config_id is denormalized (no FK constraint)
-- so pipeline history survives even if a config were ever removed.
CREATE TABLE deployment_pipeline (
    id            VARCHAR(36)   NOT NULL,
    config_id     VARCHAR(36)   NOT NULL,
    status        VARCHAR(50)   NOT NULL,
    error_code    VARCHAR(100),
    error_message VARCHAR(1000),
    created_at    TIMESTAMPTZ   NOT NULL,
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    CONSTRAINT pk_deployment_pipeline PRIMARY KEY (id)
);

-- ── pipeline_step ────────────────────────────────────────────────────────────
-- Individual steps within a deployment pipeline run.
CREATE TABLE pipeline_step (
    id           VARCHAR(36)  NOT NULL,
    pipeline_id  VARCHAR(36)  NOT NULL,
    step_type    VARCHAR(100) NOT NULL,
    step_order   INT          NOT NULL,
    status       VARCHAR(50)  NOT NULL,
    message      VARCHAR(500),
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pk_pipeline_step PRIMARY KEY (id),
    CONSTRAINT fk_pipeline_step_pipeline
        FOREIGN KEY (pipeline_id) REFERENCES deployment_pipeline (id)
);

-- ── image_tracking_status ────────────────────────────────────────────────────
-- Source-aware availability tracking per catalog image tag.
-- Uses TIMESTAMP (no TZ) to mirror the Java LocalDateTime fields in the entity.
CREATE TABLE image_tracking_status (
    id                    VARCHAR(36)  NOT NULL,
    db_type               VARCHAR(50)  NOT NULL,
    image_name            VARCHAR(255) NOT NULL,
    image_tag             VARCHAR(255) NOT NULL,
    docker_hub_managed    BOOLEAN      NOT NULL,
    local_status          VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN',
    docker_hub_status     VARCHAR(32)  NOT NULL DEFAULT 'NOT_APPLICABLE',
    decision              VARCHAR(32)  NOT NULL DEFAULT 'ALLOW_WITH_WARNING',
    message               TEXT,
    local_checked_at      TIMESTAMP,
    docker_hub_checked_at TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP,
    CONSTRAINT pk_image_tracking_status PRIMARY KEY (id),
    CONSTRAINT uk_image_tracking_target
        UNIQUE (db_type, image_name, image_tag)
);
