--liquibase formatted sql
--changeset portWrangler:3 labels:template-unify comment:Merge config_template into deployment_config; templates are rows with is_template=true

-- deploy_method is a deploy-time concept; template rows have no method
ALTER TABLE deployment_config ALTER COLUMN deploy_method DROP NOT NULL;

-- New template-specific columns (defaults keep all existing instance rows unchanged)
ALTER TABLE deployment_config ADD COLUMN IF NOT EXISTS is_template  BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE deployment_config ADD COLUMN IF NOT EXISTS description  VARCHAR(500);
ALTER TABLE deployment_config ADD COLUMN IF NOT EXISTS deploy_count INT         NOT NULL DEFAULT 0;

-- Migrate any previously-saved templates into deployment_config.
-- container_port = 0 (placeholder; templates are never deployed directly).
-- ON CONFLICT in case V2 was never applied and config_template does not exist is handled
-- by the DROP TABLE IF EXISTS below — the INSERT simply won't run if the table is absent.
INSERT INTO deployment_config
    (id, name, description, db_type, version, host_port, container_port,
     username, password, database_name, extra_env_json,
     deploy_method, is_template, deploy_count,
     is_system, is_imported, created_at, updated_at)
SELECT
    id, name, description, db_type, version, host_port, 0,
    username, password, database_name, extra_env_json,
    NULL, TRUE, deploy_count,
    FALSE, FALSE, created_at, updated_at
FROM config_template
ON CONFLICT (id) DO NOTHING;

DROP TABLE IF EXISTS config_template;
