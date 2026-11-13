ALTER TABLE room_types
    ADD COLUMN policy_snapshot_version BIGINT NOT NULL DEFAULT 0;

