--liquibase formatted sql
--changeset portWrangler:7 labels:phase3 comment:Chat memory + sessions

-- Spring AI JdbcChatMemoryRepository default table (verbatim window storage).
CREATE TABLE SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36)  NOT NULL,
    content         TEXT         NOT NULL,
    type            VARCHAR(10)  NOT NULL,           -- USER | ASSISTANT | SYSTEM | TOOL
    "timestamp"     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_sacm_type CHECK (type IN ('USER','ASSISTANT','SYSTEM','TOOL'))
);
CREATE INDEX idx_sacm_conv_time ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");

-- Session + rolling-summary state (drives §3 token optimization).
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

-- Per-message store with retrieval signals (access_count/last_seen feed §3.6 scoring).
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
