ALTER TABLE users ADD COLUMN identity_id UUID;
CREATE UNIQUE INDEX uq_users_identity_id ON users(identity_id) WHERE identity_id IS NOT NULL;

CREATE TABLE identity_projection_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

