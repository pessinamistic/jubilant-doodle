ALTER TABLE deployed_container
    ADD COLUMN container_port INT NOT NULL DEFAULT 0;