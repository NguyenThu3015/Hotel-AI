CREATE TABLE reservation_payment_projections (
    reservation_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    reference VARCHAR(30),
    status VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    deposit_required NUMERIC(19,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    stay_total_amount NUMERIC(19,2),
    stay_paid_amount NUMERIC(19,2),
    stay_updated_at TIMESTAMPTZ,
    source_version BIGINT NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_projection_money CHECK (
        total_amount >= 0 AND deposit_required >= 0 AND paid_amount >= 0
        AND deposit_required <= total_amount
    )
);
CREATE INDEX idx_payment_projection_customer ON reservation_payment_projections(customer_id);
CREATE INDEX idx_payment_projection_email ON reservation_payment_projections(contact_email);

CREATE TABLE pending_stay_folio_projections (
    reservation_id UUID PRIMARY KEY,
    currency CHAR(3) NOT NULL,
    charges_total NUMERIC(19,2) NOT NULL,
    paid_total NUMERIC(19,2) NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pending_stay_folio_money CHECK (
        charges_total >= 0 AND paid_total >= 0
    )
);

CREATE TABLE payment_intents (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    created_by_identity_id UUID NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    reservation_total_snapshot NUMERIC(19,2) NOT NULL,
    reservation_deposit_snapshot NUMERIC(19,2) NOT NULL,
    reservation_paid_snapshot NUMERIC(19,2) NOT NULL,
    reservation_version_snapshot BIGINT NOT NULL,
    reservation_snapshot_hash CHAR(64) NOT NULL,
    provider_order_id VARCHAR(100) NOT NULL UNIQUE,
    provider_request_id VARCHAR(100) UNIQUE,
    provider_transaction_id VARCHAR(160) UNIQUE,
    provider_extra_data TEXT,
    checkout_url TEXT,
    failure_code VARCHAR(80),
    failure_message VARCHAR(500),
    staff_note VARCHAR(500),
    expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_purpose CHECK (purpose IN ('DEPOSIT','FULL','BALANCE')),
    CONSTRAINT ck_payment_provider CHECK (provider IN ('MOMO','SANDBOX','CASH')),
    CONSTRAINT ck_payment_status CHECK (status IN (
        'CREATED','PENDING','SUCCEEDED','FAILED','CANCELLED','EXPIRED'
    ))
);
CREATE INDEX idx_payment_reservation_created ON payment_intents(reservation_id, created_at DESC);
CREATE INDEX idx_payment_customer_created ON payment_intents(customer_id, created_at DESC);
CREATE UNIQUE INDEX uq_active_payment_purpose
    ON payment_intents(reservation_id, purpose)
    WHERE status IN ('CREATED','PENDING');

CREATE TABLE refund_requests (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment_intents(id),
    amount NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_by UUID NOT NULL,
    approved_by UUID,
    rejected_by UUID,
    decision_reason VARCHAR(500),
    provider_refund_id VARCHAR(160) UNIQUE,
    provider_response TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_status CHECK (status IN (
        'REQUESTED','APPROVED','PROCESSING','COMPLETED','REJECTED','FAILED'
    ))
);
CREATE INDEX idx_refund_payment_created ON refund_requests(payment_id, requested_at DESC);

CREATE TABLE payment_idempotency_records (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL,
    command_scope VARCHAR(120) NOT NULL,
    actor_id UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_payment_idempotency UNIQUE (idempotency_key, command_scope, actor_id)
);
CREATE INDEX idx_payment_idempotency_expiry ON payment_idempotency_records(expires_at);

CREATE TABLE payment_webhook_deliveries (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_event_key VARCHAR(320) NOT NULL,
    raw_payload TEXT NOT NULL,
    raw_payload_sha256 CHAR(64) NOT NULL,
    signature_sha256 CHAR(64),
    signature_valid BOOLEAN NOT NULL,
    replay_count INTEGER NOT NULL DEFAULT 0,
    payment_id UUID,
    provider_transaction_id VARCHAR(160),
    processing_result VARCHAR(80),
    failure_detail VARCHAR(500),
    correlation_id VARCHAR(120) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_webhook_event UNIQUE (provider, provider_event_key)
);
CREATE INDEX idx_webhook_received ON payment_webhook_deliveries(received_at DESC);

CREATE TABLE payment_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_payment_outbox_pending ON payment_outbox_events(occurred_at)
    WHERE published_at IS NULL;

CREATE TABLE payment_inbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    producer VARCHAR(80) NOT NULL,
    aggregate_id UUID,
    correlation_id VARCHAR(120),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);
CREATE INDEX idx_payment_inbox_received ON payment_inbox_events(received_at);
