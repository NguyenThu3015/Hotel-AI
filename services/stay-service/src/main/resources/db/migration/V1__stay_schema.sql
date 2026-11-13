CREATE TABLE reservation_stay_projections (
    reservation_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    reference VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    room_type_id UUID NOT NULL,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    room_count INTEGER NOT NULL,
    adults INTEGER NOT NULL,
    children INTEGER NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(40) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    deposit_required NUMERIC(19,2) NOT NULL,
    paid_amount NUMERIC(19,2) NOT NULL,
    inventory_hold_id UUID,
    source_version BIGINT NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_stay_projection_dates CHECK (check_out > check_in),
    CONSTRAINT ck_stay_projection_counts CHECK (
        room_count > 0 AND adults > 0 AND children >= 0
    ),
    CONSTRAINT ck_stay_projection_money CHECK (
        total_amount >= 0 AND deposit_required >= 0 AND paid_amount >= 0
    )
);
CREATE INDEX idx_stay_projection_search
    ON reservation_stay_projections(status, check_in DESC);

CREATE TABLE room_type_projections (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(160) NOT NULL,
    max_adults INTEGER NOT NULL,
    max_children INTEGER NOT NULL,
    max_occupancy INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    source_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE room_projections (
    id UUID PRIMARY KEY,
    room_number VARCHAR(30) NOT NULL,
    room_type_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL,
    current_stay_id UUID,
    source_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_stay_room_projection_type_status
    ON room_projections(room_type_id, status, active);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(140) NOT NULL,
    producer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stays (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    reservation_reference VARCHAR(40) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    scheduled_check_in DATE NOT NULL,
    scheduled_check_out DATE NOT NULL,
    actual_check_in TIMESTAMPTZ,
    actual_check_out TIMESTAMPTZ,
    adults INTEGER NOT NULL,
    children INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    charges_total NUMERIC(19,2) NOT NULL,
    paid_total NUMERIC(19,2) NOT NULL,
    outstanding_deposit_at_check_in NUMERIC(19,2) NOT NULL,
    check_in_request_hash VARCHAR(64) NOT NULL,
    created_by UUID NOT NULL,
    checkout_override_approved_by UUID,
    checkout_override_reason VARCHAR(500),
    last_saga_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_stay_dates CHECK (scheduled_check_out > scheduled_check_in),
    CONSTRAINT ck_stay_money CHECK (
        charges_total >= 0 AND paid_total >= 0 AND outstanding_deposit_at_check_in >= 0
    )
);
CREATE INDEX idx_stays_status_updated ON stays(status, updated_at);

CREATE TABLE stay_guests (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL REFERENCES stays(id),
    full_name VARCHAR(180) NOT NULL,
    date_of_birth DATE NOT NULL,
    nationality VARCHAR(80) NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    document_number_ciphertext TEXT NOT NULL,
    document_number_last4 VARCHAR(8) NOT NULL,
    primary_guest BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_stay_primary_guest
    ON stay_guests(stay_id) WHERE primary_guest = TRUE;

CREATE TABLE room_assignments (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL REFERENCES stays(id),
    room_id UUID NOT NULL,
    room_number VARCHAR(30) NOT NULL,
    room_type_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    reason VARCHAR(500) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_room_assignments_stay ON room_assignments(stay_id, status);
CREATE UNIQUE INDEX uq_active_room_assignment
    ON room_assignments(room_id) WHERE status = 'ACTIVE';

CREATE TABLE stay_operations (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL REFERENCES stays(id),
    type VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    from_room_id UUID,
    target_room_id UUID,
    previous_checkout_date DATE,
    requested_checkout_date DATE,
    amount_delta NUMERIC(19,2),
    reason VARCHAR(500),
    requested_by UUID NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_stay_operation_idempotency
        UNIQUE(stay_id, type, idempotency_key)
);
CREATE INDEX idx_stay_operations_pending ON stay_operations(status, updated_at);

CREATE TABLE stay_folio_entries (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL REFERENCES stays(id),
    operation_id UUID NOT NULL,
    entry_type VARCHAR(40) NOT NULL,
    description VARCHAR(300) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_stay_folio_amount CHECK (amount <> 0)
);
CREATE INDEX idx_stay_folio_stay ON stay_folio_entries(stay_id, recorded_at);

CREATE TABLE stay_outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(140) NOT NULL,
    correlation_id VARCHAR(120) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_stay_outbox_pending
    ON stay_outbox_events(published_at, occurred_at);
