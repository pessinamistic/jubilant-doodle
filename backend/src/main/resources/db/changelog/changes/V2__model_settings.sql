--liquibase formatted sql
--changeset portWrangler:2 labels:phase2 comment:Per-model runtime settings (temperature, context window, keep-alive)

-- Nullable: a NULL means "use the runtime default" — only user-tuned values are stored.
ALTER TABLE pulled_model ADD COLUMN temperature DOUBLE PRECISION;
ALTER TABLE pulled_model ADD COLUMN num_ctx INT;
ALTER TABLE pulled_model ADD COLUMN keep_alive VARCHAR(40);
