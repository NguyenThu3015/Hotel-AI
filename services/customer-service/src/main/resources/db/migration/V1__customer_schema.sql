CREATE TABLE customer_profiles (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL UNIQUE,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL,
    phone VARCHAR(32),
    date_of_birth DATE,
    gender VARCHAR(16),
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'vi',
    room_preferences TEXT NOT NULL DEFAULT '{}',
    marketing_email BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_sms BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_zalo BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    identity_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    roles_snapshot VARCHAR(500) NOT NULL DEFAULT 'CUSTOMER',
    consent_updated_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    merged_into_customer_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'MERGED', 'ANONYMIZED')),
    CONSTRAINT ck_not_merged_into_self CHECK (merged_into_customer_id IS NULL OR merged_into_customer_id <> id)
);

CREATE INDEX idx_customer_name_lower ON customer_profiles (LOWER(full_name));
CREATE INDEX idx_customer_email_lower ON customer_profiles (LOWER(email));
CREATE INDEX idx_customer_phone ON customer_profiles (phone);

CREATE TABLE customer_notes (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customer_profiles(id),
    note TEXT NOT NULL,
    created_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_customer_notes_customer ON customer_notes (customer_id, created_at DESC);

CREATE TABLE customer_merge_proposals (
    id UUID PRIMARY KEY,
    source_customer_id UUID NOT NULL REFERENCES customer_profiles(id),
    target_customer_id UUID NOT NULL REFERENCES customer_profiles(id),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    proposed_by_identity_id UUID NOT NULL,
    reviewed_by_identity_id UUID,
    review_comment TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_merge_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_merge_different_customers CHECK (source_customer_id <> target_customer_id)
);
CREATE UNIQUE INDEX uq_pending_customer_merge
    ON customer_merge_proposals(source_customer_id, target_customer_id)
    WHERE status = 'PENDING';

CREATE TABLE customer_audit_log (
    id UUID PRIMARY KEY,
    customer_id UUID,
    actor_identity_id UUID,
    action VARCHAR(80) NOT NULL,
    details TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_customer_audit_customer ON customer_audit_log (customer_id, occurred_at DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    correlation_id UUID NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_customer_outbox_unpublished ON outbox_events (occurred_at) WHERE published_at IS NULL;

CREATE TABLE processed_identity_events (
    event_id VARCHAR(120) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

-- identity_id and actor identity columns are external references by design; no cross-service foreign keys.
