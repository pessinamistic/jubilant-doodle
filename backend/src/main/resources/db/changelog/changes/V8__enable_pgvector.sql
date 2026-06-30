--liquibase formatted sql
--changeset portWrangler:8 labels:phase4 comment:pgvector + shared vector store

CREATE EXTENSION IF NOT EXISTS vector;

-- ONE shared store for ALL embeddings (chat messages, deployments, logs, kafka metadata),
-- discriminated by metadata->>'type'. Matches Spring AI PgVectorStore's default table so the
-- starter can manage it; one HNSW index instead of N.
CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(768)                       -- nomic-embed-text; FIXED at create time (§2.3)
);

-- HNSW: better query performance than IVFFlat AND builds on an empty table (no training step) —
-- ideal for a fresh local install. Cosine matches Spring AI's default distance.
CREATE INDEX idx_vector_store_hnsw ON vector_store
    USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- GIN on metadata so metadata filtering (type, session_id, instance_type, level) is indexed.
CREATE INDEX idx_vector_store_metadata ON vector_store USING gin (metadata jsonb_path_ops);
