--liquibase formatted sql
--changeset portWrangler:2 labels:config-template comment:Add config_template table and template_id on deployment_config

-- ── config_template ──────────────────────────────────────────────────────────
-- Reusable configuration blueprints. One template may drive many deployments.
CREATE TABLE config_template (
    id             VARCHAR(36)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    description    VARCHAR(500),
    db_type        VARCHAR(50)  NOT NULL,
    version        VARCHAR(255) NOT NULL,
    host_port      INT          NOT NULL,
    username       VARCHAR(255),
    password       VARCHAR(255),
    database_name  VARCHAR(255),
    extra_env_json TEXT,
    deploy_count   INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ,
    CONSTRAINT pk_config_template PRIMARY KEY (id),
    CONSTRAINT uq_config_template_name UNIQUE (name)
);

-- Link deployed instances back to the template they were launched from.
-- Intentionally no FK constraint: templates may be deleted while instances remain.
ALTER TABLE deployment_config
    ADD COLUMN template_id VARCHAR(36);
