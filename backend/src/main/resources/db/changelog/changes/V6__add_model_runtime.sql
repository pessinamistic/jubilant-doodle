--liquibase formatted sql
--changeset portWrangler:6 labels:phase2 comment:LLM runtimes + pulled models

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
