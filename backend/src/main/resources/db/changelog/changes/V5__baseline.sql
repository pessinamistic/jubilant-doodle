--liquibase formatted sql
--changeset portWrangler:5 labels:baseline comment:Reconcile Hibernate-managed drift; switch to Liquibase-only DDL management

-- ── deployed_container ───────────────────────────────────────────────────────
-- host_port was tracked by the entity but never in V1-V4 (Hibernate added it via
-- ddl-auto:update). Fresh installs need it; existing installs already have it (no-op).
ALTER TABLE deployed_container ADD COLUMN IF NOT EXISTS host_port INT NOT NULL DEFAULT 0;

-- ── deployment_pipeline ──────────────────────────────────────────────────────
-- deployment_container_id and is_template were added by Hibernate but are absent from V1.
-- DEFAULT '' / DEFAULT FALSE satisfy NOT NULL for any pre-existing rows.
ALTER TABLE deployment_pipeline
    ADD COLUMN IF NOT EXISTS deployment_container_id VARCHAR(36) NOT NULL DEFAULT '';
ALTER TABLE deployment_pipeline
    ADD COLUMN IF NOT EXISTS is_template BOOLEAN NOT NULL DEFAULT FALSE;

-- ── deployment_config ────────────────────────────────────────────────────────
-- V2 added template_id; V3 consolidated templates into deployment_config rows but never
-- dropped the column.  The entity has never mapped it.  Drop the orphaned column.
-- On existing Hibernate-managed installs the column was never added (no-op).
ALTER TABLE deployment_config DROP COLUMN IF EXISTS template_id;
