--liquibase formatted sql
--changeset portWrangler:1 labels:baseline comment:Baseline schema for Port Wrangler system database

-- ── deployment_config ────────────────────────────────────────────────────────
-- Stable, user-facing configuration record for a deployed database instance.
-- Rows are never deleted: REMOVED state is recorded on the container side.
-- is_template rows are reusable blueprints (deploy_method is NULL for these).
CREATE TABLE deployment_config (
    id               VARCHAR(36)  NOT NULL,
    name             VARCHAR(255) NOT NULL,
    db_type          VARCHAR(50)  NOT NULL,
    version          VARCHAR(255) NOT NULL,
    host_port        INT          NOT NULL,
    container_port   INT          NOT NULL DEFAULT 0,  -- dead column, unmapped by DeploymentConfig entity; kept for H2DataMigrator compat
    username         VARCHAR(255),
    password         VARCHAR(255),
    database_name    VARCHAR(255),
    extra_env_json   TEXT,
    deploy_method    VARCHAR(50),
    is_system        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_imported      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_template      BOOLEAN      NOT NULL DEFAULT FALSE,
    description      VARCHAR(500),
    deploy_count     INT          NOT NULL DEFAULT 0,
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
    host_port           INT           NOT NULL DEFAULT 0,
    container_port       INT          NOT NULL DEFAULT 0,
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
    id                        VARCHAR(36)   NOT NULL,
    config_id                 VARCHAR(36)   NOT NULL,
    deployment_container_id   VARCHAR(36)   NOT NULL DEFAULT '',
    is_template                BOOLEAN      NOT NULL DEFAULT FALSE,
    status                    VARCHAR(50)   NOT NULL,
    error_code                VARCHAR(100),
    error_message             VARCHAR(1000),
    created_at                TIMESTAMPTZ   NOT NULL,
    started_at                TIMESTAMPTZ,
    completed_at              TIMESTAMPTZ,
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

-- ── model_runtime / pulled_model ─────────────────────────────────────────────
-- LLM runtimes (Ollama / Docker Model Runner) and the models pulled into them.
CREATE TABLE model_runtime (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    runtime_type  VARCHAR(40)  NOT NULL,             -- OLLAMA | DOCKER_MODEL_RUNNER
    config_id     VARCHAR(36)  REFERENCES deployment_config(id),  -- the managed container
    base_url      VARCHAR(255) NOT NULL,
    gpu_vendor    VARCHAR(20),                        -- NVIDIA | AMD | APPLE | NONE
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE pulled_model (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,
    runtime_id   VARCHAR(36)  NOT NULL REFERENCES model_runtime(id),
    model_name   VARCHAR(255) NOT NULL,               -- e.g. llama3.1:8b
    size_bytes   BIGINT,
    quantization VARCHAR(40),
    digest       VARCHAR(255),
    pulled_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ,
    CONSTRAINT uq_pulled_model UNIQUE (runtime_id, model_name)
);

-- ── chat memory + sessions ───────────────────────────────────────────────────
-- Spring AI JdbcChatMemoryRepository default table (verbatim window storage).
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36)  NOT NULL,
    content         TEXT         NOT NULL,
    type            VARCHAR(10)  NOT NULL,           -- USER | ASSISTANT | SYSTEM | TOOL
    "timestamp"     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_sacm_type CHECK (type IN ('USER','ASSISTANT','SYSTEM','TOOL'))
);
CREATE INDEX idx_sacm_conv_time ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");

-- Session + rolling-summary state (drives token optimization).
CREATE TABLE chat_session (
    id                       VARCHAR(36)  NOT NULL PRIMARY KEY,
    title                    VARCHAR(255),
    system_prompt            TEXT,
    model_runtime_id         VARCHAR(36),            -- FK to model_runtime (nullable: default runtime)
    model_id                 VARCHAR(255),
    rolling_summary          TEXT,
    summary_token_count      INT          NOT NULL DEFAULT 0,
    summarized_through_seq   INT          NOT NULL DEFAULT 0,
    current_seq              INT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ
);

-- Per-message store with retrieval signals (access_count/last_seen feed relevance scoring).
CREATE TABLE chat_message (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    session_id    VARCHAR(36)  NOT NULL REFERENCES chat_session(id),
    seq           INT          NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    content       TEXT         NOT NULL,
    token_count   INT          NOT NULL DEFAULT 0,
    access_count  INT          NOT NULL DEFAULT 0,
    last_seen     TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_chat_message_seq UNIQUE (session_id, seq)
);
CREATE INDEX idx_chat_message_session_seq ON chat_message (session_id, seq);

-- ── pgvector ──────────────────────────────────────────────────────────────────
-- ONE shared store for ALL embeddings (chat messages, deployments, logs, kafka metadata),
-- discriminated by metadata->>'type'. Matches Spring AI PgVectorStore's default table so the
-- starter can manage it; one HNSW index instead of N.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(768)                       -- nomic-embed-text; FIXED at create time
);

-- HNSW: better query performance than IVFFlat AND builds on an empty table (no training step) —
-- ideal for a fresh local install. Cosine matches Spring AI's default distance.
CREATE INDEX idx_vector_store_hnsw ON vector_store
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- GIN on metadata so metadata filtering (type, session_id, instance_type, level) is indexed.
CREATE INDEX idx_vector_store_metadata ON vector_store USING gin (metadata jsonb_path_ops);
