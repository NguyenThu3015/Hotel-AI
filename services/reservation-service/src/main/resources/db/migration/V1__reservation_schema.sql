CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    reference VARCHAR(20) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    created_by_identity_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    room_type_id UUID NOT NULL,
    inventory_hold_id UUID NOT NULL UNIQUE,
    inventory_hold_version BIGINT NOT NULL DEFAULT 0,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    room_count INTEGER NOT NULL,
    adults INTEGER NOT NULL,
    children INTEGER NOT NULL DEFAULT 0,
    contact_name VARCHAR(160) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    special_requests VARCHAR(1000),
    pricing_quote_id UUID NOT NULL,
    pricing_version VARCHAR(80) NOT NULL,
    currency CHAR(3) NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(19,2) NOT NULL,
    deposit_required NUMERIC(19,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    manual_confirmation_required BOOLEAN NOT NULL DEFAULT FALSE,
    price_breakdown JSONB NOT NULL,
    cancellation_policy JSONB NOT NULL,
    free_cancellation_until TIMESTAMPTZ,
    cancellation_fee_after_cutoff NUMERIC(19,2) NOT NULL DEFAULT 0,
    cancellation_fee_applied NUMERIC(19,2) NOT NULL DEFAULT 0,
    hold_expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_reservation_dates CHECK (check_out > check_in),
    CONSTRAINT ck_reservation_guests CHECK (room_count > 0 AND adults > 0 AND children >= 0),
    CONSTRAINT ck_reservation_money CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND
        total_amount >= 0 AND deposit_required >= 0 AND paid_amount >= 0 AND
        cancellation_fee_after_cutoff >= 0 AND cancellation_fee_applied >= 0
    ),
    CONSTRAINT ck_reservation_status CHECK (status IN (
        'DRAFT','HOLD','PENDING_PAYMENT','PENDING_CONFIRMATION','CONFIRMED',
        'CHECKED_IN','CHECKED_OUT','EXPIRED','REJECTED','CANCELLED','NO_SHOW'
    )),
    CONSTRAINT ck_reservation_channel CHECK (channel IN ('ONLINE','PHONE','WALK_IN','STAFF'))
);

CREATE INDEX idx_reservations_customer_created ON reservations(customer_id, created_at DESC);
CREATE INDEX idx_reservations_status_check_in ON reservations(status, check_in);
CREATE INDEX idx_reservations_hold_expiry ON reservations(hold_expires_at)
    WHERE status IN ('HOLD','PENDING_PAYMENT','PENDING_CONFIRMATION');

CREATE TABLE reservation_guests (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    full_name VARCHAR(160) NOT NULL,
    document_type VARCHAR(30),
    document_number VARCHAR(80),
    nationality VARCHAR(2),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reservation_guests_reservation ON reservation_guests(reservation_id);

CREATE TABLE reservation_change_requests (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    requested_by UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_change JSONB NOT NULL,
    decision_reason VARCHAR(500),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_change_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN'))
);
CREATE INDEX idx_change_requests_reservation ON reservation_change_requests(reservation_id, created_at DESC);
CREATE UNIQUE INDEX uq_change_request_pending ON reservation_change_requests(reservation_id)
    WHERE status = 'PENDING';

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL,
    command_scope VARCHAR(100) NOT NULL,
    actor_id UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_scope UNIQUE (idempotency_key, command_scope, actor_id)
);
CREATE INDEX idx_idempotency_expiry ON idempotency_records(expires_at);

CREATE TABLE outbox_events (
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
CREATE INDEX idx_outbox_pending ON outbox_events(occurred_at) WHERE published_at IS NULL;

CREATE TABLE inbox_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    producer VARCHAR(80) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    aggregate_id UUID,
    correlation_id VARCHAR(120)
);
CREATE INDEX idx_inbox_received ON inbox_events(received_at);
