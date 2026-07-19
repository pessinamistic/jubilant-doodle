--liquibase formatted sql
--changeset portWrangler:3 labels:phase2 comment:Backfill deploy_method=DOCKER for legacy non-template instances (templates stay NULL)

-- Legacy instance rows written before deploy_method existed carry NULL; historically those were
-- always Docker deployments (see DeploymentConfig.effectiveDeployMethod, the null-is-Docker default).
-- Template rows legitimately keep NULL — they are engine-agnostic blueprints (see the
-- deployment_config comment in V1__baseline.sql). Column stays nullable; no DDL here.
-- Idempotent: the WHERE clause only touches rows still NULL, so a re-run is a no-op. This is hygiene,
-- not a correctness dependency — the accessor already makes the app behave correctly without it.
UPDATE deployment_config
   SET deploy_method = 'DOCKER'
 WHERE deploy_method IS NULL
   AND is_template = FALSE;
