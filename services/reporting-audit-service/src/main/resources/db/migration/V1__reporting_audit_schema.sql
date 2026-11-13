CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    producer VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE domain_event_facts (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(160) NOT NULL,
    producer VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100),
    aggregate_id UUID,
    correlation_id VARCHAR(160),
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL
);
CREATE INDEX idx_domain_event_type_time
    ON domain_event_facts(event_type, occurred_at DESC);

CREATE TABLE reservation_facts (
    reservation_id UUID PRIMARY KEY,
    reference VARCHAR(40),
    customer_id UUID,
    channel VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    status VARCHAR(40) NOT NULL,
    room_type_id UUID,
    check_in DATE,
    check_out DATE,
    room_count INTEGER NOT NULL DEFAULT 0,
    currency VARCHAR(3),
    total_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_reservation_fact_created ON reservation_facts(created_at);
CREATE INDEX idx_reservation_fact_stay ON reservation_facts(check_in, check_out, status);

CREATE TABLE room_type_facts (
    room_type_id UUID PRIMARY KEY,
    code VARCHAR(80),
    name VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    source_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE room_facts (
    room_id UUID PRIMARY KEY,
    room_type_id UUID,
    room_number VARCHAR(40),
    status VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    source_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE payment_facts (
    payment_id UUID PRIMARY KEY,
    reservation_id UUID,
    amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    refunded_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3),
    status VARCHAR(40) NOT NULL,
    provider VARCHAR(40),
    occurred_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payment_fact_time ON payment_facts(occurred_at, status);

CREATE TABLE refund_facts (
    refund_id UUID PRIMARY KEY,
    payment_id UUID,
    reservation_id UUID,
    amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3),
    status VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refund_fact_time ON refund_facts(occurred_at, status);

CREATE TABLE service_usage_facts (
    usage_id UUID PRIMARY KEY,
    stay_id UUID,
    reservation_id UUID,
    service_id UUID,
    service_code VARCHAR(80),
    service_name VARCHAR(200),
    quantity NUMERIC(12,3) NOT NULL DEFAULT 0,
    gross_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3),
    status VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_revision INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_service_usage_fact_time ON service_usage_facts(occurred_at, status);

CREATE TABLE review_facts (
    review_id UUID PRIMARY KEY,
    rating INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    topics JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_chain_state (
    id SMALLINT PRIMARY KEY,
    last_sequence BIGINT NOT NULL,
    last_hash CHAR(64) NOT NULL,
    CONSTRAINT ck_single_audit_chain CHECK (id = 1)
);
INSERT INTO audit_chain_state(id, last_sequence, last_hash)
VALUES (1, 0, REPEAT('0', 64));

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    sequence_no BIGINT NOT NULL UNIQUE,
    source_event_id UUID UNIQUE,
    actor_id UUID,
    action VARCHAR(160) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(160),
    producer VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(160),
    ip_address VARCHAR(80),
    before_state JSONB,
    after_state JSONB,
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    previous_hash CHAR(64) NOT NULL,
    integrity_hash CHAR(64) NOT NULL UNIQUE
);
CREATE INDEX idx_audit_time ON audit_logs(occurred_at DESC);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id, occurred_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_action ON audit_logs(action, occurred_at DESC);

CREATE TABLE report_export_jobs (
    id UUID PRIMARY KEY,
    requested_by UUID NOT NULL,
    format VARCHAR(10) NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    filters JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    file_name VARCHAR(240),
    content_type VARCHAR(160),
    content BYTEA,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    CONSTRAINT ck_report_export_format CHECK (format IN ('XLSX', 'PDF', 'CSV')),
    CONSTRAINT ck_report_export_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);
CREATE INDEX idx_report_export_pending ON report_export_jobs(created_at)
    WHERE status = 'PENDING';
