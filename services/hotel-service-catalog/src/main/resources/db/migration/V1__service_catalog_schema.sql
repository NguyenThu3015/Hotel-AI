CREATE TABLE hotel_services (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    service_days VARCHAR(100) NOT NULL,
    service_start_time TIME,
    service_end_time TIME,
    service_conditions VARCHAR(1000),
    active BOOLEAN NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_service_schedule_pair CHECK (
        (service_start_time IS NULL AND service_end_time IS NULL)
        OR (service_start_time IS NOT NULL AND service_end_time IS NOT NULL
            AND service_start_time < service_end_time)
    )
);

CREATE UNIQUE INDEX uq_hotel_services_code_ci ON hotel_services (LOWER(code));
CREATE INDEX ix_hotel_services_active_code ON hotel_services (active, code);

CREATE TABLE service_price_versions (
    id UUID PRIMARY KEY,
    service_id UUID NOT NULL REFERENCES hotel_services(id),
    version_number BIGINT NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    tax_rate NUMERIC(7,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_service_price_version UNIQUE (service_id, version_number),
    CONSTRAINT ck_service_price_nonnegative CHECK (unit_price >= 0),
    CONSTRAINT ck_service_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 1),
    CONSTRAINT ck_service_price_period CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE UNIQUE INDEX uq_service_current_price
    ON service_price_versions(service_id) WHERE valid_to IS NULL;
CREATE INDEX ix_service_price_effective
    ON service_price_versions(service_id, valid_from DESC);

CREATE TABLE stay_projections (
    stay_id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    reservation_reference VARCHAR(40) NOT NULL,
    contact_name VARCHAR(180),
    status VARCHAR(30) NOT NULL,
    scheduled_check_in DATE NOT NULL,
    scheduled_check_out DATE NOT NULL,
    room_numbers VARCHAR(500) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    charges_total NUMERIC(19,2) NOT NULL,
    paid_total NUMERIC(19,2) NOT NULL,
    balance NUMERIC(19,2) NOT NULL,
    source_updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_service_stays_reference ON stay_projections(reservation_reference);
CREATE INDEX ix_service_stays_status_updated ON stay_projections(status, source_updated_at DESC);

CREATE TABLE service_usages (
    id UUID PRIMARY KEY,
    stay_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    service_id UUID NOT NULL REFERENCES hotel_services(id),
    service_code VARCHAR(50) NOT NULL,
    service_name VARCHAR(160) NOT NULL,
    unit VARCHAR(30) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    tax_rate NUMERIC(7,4) NOT NULL,
    net_amount NUMERIC(19,2) NOT NULL,
    tax_amount NUMERIC(19,2) NOT NULL,
    gross_amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    revision INTEGER NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    cancelled_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_service_usage_quantity CHECK (quantity > 0),
    CONSTRAINT ck_service_usage_amounts CHECK (
        unit_price >= 0 AND tax_rate >= 0 AND tax_rate <= 1
        AND net_amount >= 0 AND tax_amount >= 0 AND gross_amount >= 0
    ),
    CONSTRAINT ck_service_usage_status CHECK (status IN ('PLANNED', 'PERFORMED', 'CANCELLED'))
);

CREATE INDEX ix_service_usages_stay_time ON service_usages(stay_id, occurred_at DESC);
CREATE INDEX ix_service_usages_service ON service_usages(service_id);

CREATE TABLE service_usage_revisions (
    id UUID PRIMARY KEY,
    usage_id UUID NOT NULL REFERENCES service_usages(id),
    revision_number INTEGER NOT NULL,
    action VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    tax_rate NUMERIC(7,4) NOT NULL,
    gross_amount NUMERIC(19,2) NOT NULL,
    delta_amount NUMERIC(19,2) NOT NULL,
    usage_status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500),
    changed_by UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_service_usage_revision UNIQUE (usage_id, revision_number)
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(140) NOT NULL,
    producer VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE catalog_outbox_events (
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

CREATE INDEX ix_catalog_outbox_pending
    ON catalog_outbox_events(occurred_at) WHERE published_at IS NULL;
