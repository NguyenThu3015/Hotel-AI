CREATE TABLE reservation_billing_projections (
    reservation_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    reference VARCHAR(40) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    room_count INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    discount_amount NUMERIC(19,2) NOT NULL,
    tax_amount NUMERIC(19,2) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    paid_amount NUMERIC(19,2) NOT NULL,
    price_breakdown JSONB NOT NULL,
    source_version BIGINT NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_billing_reservation_dates CHECK (check_out > check_in),
    CONSTRAINT ck_billing_reservation_money CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND total_amount >= 0 AND paid_amount >= 0
    )
);

CREATE TABLE stay_billing_projections (
    stay_id UUID PRIMARY KEY,
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
    room_numbers VARCHAR(500) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    charges_total NUMERIC(19,2) NOT NULL,
    paid_total NUMERIC(19,2) NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_billing_stays_status_updated
    ON stay_billing_projections(status, source_updated_at DESC);

CREATE TABLE stay_folio_line_projections (
    entry_id UUID PRIMARY KEY,
    stay_id UUID NOT NULL,
    source_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    description VARCHAR(300) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_billing_folio_nonzero CHECK (amount <> 0)
);

CREATE INDEX ix_billing_folio_stay
    ON stay_folio_line_projections(stay_id, recorded_at);

CREATE TABLE payment_ledger_entries (
    event_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(200),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_billing_payment_type CHECK (type IN ('PAYMENT', 'REFUND')),
    CONSTRAINT ck_billing_payment_positive CHECK (amount > 0)
);

CREATE INDEX ix_billing_payment_reservation
    ON payment_ledger_entries(reservation_id, occurred_at);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(140) NOT NULL,
    producer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    reservation_reference VARCHAR(40) NOT NULL,
    invoice_number VARCHAR(40) UNIQUE,
    status VARCHAR(20) NOT NULL,
    buyer_name VARCHAR(200) NOT NULL,
    buyer_email VARCHAR(320) NOT NULL,
    buyer_address VARCHAR(500) NOT NULL,
    buyer_tax_code VARCHAR(20),
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    discount_amount NUMERIC(19,2) NOT NULL,
    tax_amount NUMERIC(19,2) NOT NULL,
    folio_amount NUMERIC(19,2) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    paid_amount NUMERIC(19,2) NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    draft_idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    draft_request_hash VARCHAR(64) NOT NULL,
    issue_idempotency_key VARCHAR(160) UNIQUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    issued_by UUID,
    issued_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'CANCELLED')),
    CONSTRAINT ck_invoice_nonnegative CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND tax_amount >= 0
        AND total_amount >= 0 AND paid_amount >= 0
    )
);

CREATE UNIQUE INDEX uq_billing_active_invoice_stay
    ON invoices(stay_id) WHERE status = 'ISSUED';
CREATE INDEX ix_billing_invoice_stay_created
    ON invoices(stay_id, created_at DESC);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    category VARCHAR(20) NOT NULL,
    source_id UUID,
    description VARCHAR(300) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    display_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_invoice_line_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_invoice_lines_invoice
    ON invoice_lines(invoice_id, display_order);

CREATE TABLE invoice_corrections (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    correction_number VARCHAR(40) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    amount_delta NUMERIC(19,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    issued_by UUID NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_invoice_correction_type CHECK (
        type IN ('ADJUSTMENT', 'CANCELLATION')
    ),
    CONSTRAINT ck_invoice_correction_nonzero CHECK (amount_delta <> 0)
);

CREATE INDEX ix_invoice_corrections_invoice
    ON invoice_corrections(invoice_id, issued_at);

CREATE TABLE invoice_number_sequences (
    series_year VARCHAR(20) PRIMARY KEY,
    next_value BIGINT NOT NULL,
    CONSTRAINT ck_invoice_sequence_positive CHECK (next_value > 0)
);

CREATE TABLE invoice_delivery_requests (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    recipient VARCHAR(320) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE billing_outbox_events (
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

CREATE INDEX ix_billing_outbox_pending
    ON billing_outbox_events(occurred_at) WHERE published_at IS NULL;
