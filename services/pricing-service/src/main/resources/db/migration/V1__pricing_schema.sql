CREATE TABLE room_type_references (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL,
    source_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_pricing_room_type_code_lower ON room_type_references (LOWER(code));

CREATE TABLE base_rate_versions (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL,
    version_no INTEGER NOT NULL,
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    stay_from DATE NOT NULL,
    stay_to DATE NOT NULL,
    booking_effective_from TIMESTAMPTZ NOT NULL,
    booking_effective_to TIMESTAMPTZ,
    outlier_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    inventory_snapshot_published_at TIMESTAMPTZ,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_base_rate_version UNIQUE (room_type_id, version_no),
    CONSTRAINT ck_base_rate_amount CHECK (amount >= 0),
    CONSTRAINT ck_base_rate_stay_period CHECK (stay_to > stay_from),
    CONSTRAINT ck_base_rate_booking_period CHECK (
        booking_effective_to IS NULL OR booking_effective_to > booking_effective_from
    )
);
CREATE INDEX idx_base_rate_quote
    ON base_rate_versions (room_type_id, stay_from, stay_to, booking_effective_from);

CREATE TABLE rate_rule_versions (
    id UUID PRIMARY KEY,
    rule_key UUID NOT NULL,
    version_no INTEGER NOT NULL,
    room_type_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    price_mode VARCHAR(30) NOT NULL,
    rule_value NUMERIC(19,4) NOT NULL,
    priority INTEGER NOT NULL,
    stay_from DATE NOT NULL,
    stay_to DATE NOT NULL,
    days_of_week VARCHAR(32) NOT NULL DEFAULT '',
    min_guests INTEGER,
    max_guests INTEGER,
    booking_effective_from TIMESTAMPTZ NOT NULL,
    booking_effective_to TIMESTAMPTZ,
    outlier_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_rate_rule_version UNIQUE (rule_key, version_no),
    CONSTRAINT ck_rate_rule_mode CHECK (
        price_mode IN ('FIXED_NIGHTLY', 'ADDITIVE_SURCHARGE', 'MULTIPLIER')
    ),
    CONSTRAINT ck_rate_rule_value CHECK (rule_value > 0),
    CONSTRAINT ck_rate_rule_priority CHECK (priority >= 0),
    CONSTRAINT ck_rate_rule_stay_period CHECK (stay_to > stay_from),
    CONSTRAINT ck_rate_rule_guest_range CHECK (
        (min_guests IS NULL OR min_guests > 0)
        AND (max_guests IS NULL OR max_guests > 0)
        AND (min_guests IS NULL OR max_guests IS NULL OR max_guests >= min_guests)
    ),
    CONSTRAINT ck_rate_rule_booking_period CHECK (
        booking_effective_to IS NULL OR booking_effective_to > booking_effective_from
    )
);
CREATE INDEX idx_rate_rule_quote
    ON rate_rule_versions (room_type_id, stay_from, stay_to, booking_effective_from, priority);

CREATE TABLE promotion_versions (
    id UUID PRIMARY KEY,
    promotion_key UUID NOT NULL,
    version_no INTEGER NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value NUMERIC(19,2) NOT NULL,
    max_discount NUMERIC(19,2),
    minimum_subtotal NUMERIC(19,2),
    minimum_nights INTEGER,
    maximum_nights INTEGER,
    applicable_room_type_ids TEXT NOT NULL DEFAULT '',
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    total_quota INTEGER,
    per_customer_quota INTEGER,
    budget_amount NUMERIC(19,2),
    used_count INTEGER NOT NULL DEFAULT 0,
    budget_used NUMERIC(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    manually_paused BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_promotion_version UNIQUE (promotion_key, version_no),
    CONSTRAINT ck_promotion_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_promotion_values CHECK (
        discount_value > 0
        AND (max_discount IS NULL OR max_discount > 0)
        AND (minimum_subtotal IS NULL OR minimum_subtotal >= 0)
        AND (budget_amount IS NULL OR budget_amount > 0)
        AND used_count >= 0
        AND budget_used >= 0
    ),
    CONSTRAINT ck_promotion_percentage CHECK (
        discount_type <> 'PERCENT' OR discount_value <= 100
    ),
    CONSTRAINT ck_promotion_period CHECK (valid_to > valid_from),
    CONSTRAINT ck_promotion_nights CHECK (
        (minimum_nights IS NULL OR minimum_nights > 0)
        AND (maximum_nights IS NULL OR maximum_nights > 0)
        AND (minimum_nights IS NULL OR maximum_nights IS NULL OR maximum_nights >= minimum_nights)
    ),
    CONSTRAINT ck_promotion_quota CHECK (
        (total_quota IS NULL OR total_quota > 0)
        AND (per_customer_quota IS NULL OR per_customer_quota > 0)
    )
);
CREATE INDEX idx_promotion_code_effective
    ON promotion_versions (LOWER(code), valid_from, valid_to);

CREATE TABLE policy_versions (
    id UUID PRIMARY KEY,
    policy_key UUID NOT NULL,
    version_no INTEGER NOT NULL,
    policy_type VARCHAR(30) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    scope_id UUID,
    name VARCHAR(180) NOT NULL,
    guest_display_text TEXT NOT NULL,
    parameters JSONB NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    inventory_snapshot_published_at TIMESTAMPTZ,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_policy_version UNIQUE (policy_key, version_no),
    CONSTRAINT ck_policy_type CHECK (
        policy_type IN ('CANCELLATION', 'NO_SHOW', 'DEPOSIT', 'FEES', 'CHECK_IN_OUT', 'CHILD', 'SURCHARGE')
    ),
    CONSTRAINT ck_policy_scope CHECK (
        (scope_type = 'GLOBAL' AND scope_id IS NULL)
        OR (scope_type = 'ROOM_TYPE' AND scope_id IS NOT NULL)
    ),
    CONSTRAINT ck_policy_effective_period CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);
CREATE INDEX idx_policy_effective
    ON policy_versions (policy_type, scope_type, scope_id, effective_from);

CREATE TABLE price_quotes (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    room_type_id UUID NOT NULL,
    customer_id UUID,
    promotion_version_id UUID,
    discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    response_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_quote_discount CHECK (discount_amount >= 0),
    CONSTRAINT ck_quote_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_price_quote_expiry ON price_quotes (expires_at);

CREATE TABLE promotion_redemptions (
    id UUID PRIMARY KEY,
    promotion_version_id UUID NOT NULL REFERENCES promotion_versions(id),
    reservation_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    quote_id UUID NOT NULL REFERENCES price_quotes(id),
    discount_amount NUMERIC(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_redemption_status CHECK (status IN ('APPLIED', 'RELEASED')),
    CONSTRAINT ck_redemption_discount CHECK (discount_amount > 0)
);
CREATE INDEX idx_redemption_customer_quota
    ON promotion_redemptions (promotion_version_id, customer_id, status);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    operation VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency_operation_key UNIQUE (operation, idempotency_key)
);
CREATE INDEX idx_idempotency_created_at ON idempotency_records (created_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(140) NOT NULL,
    correlation_id UUID NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_pricing_outbox_pending
    ON outbox_events (occurred_at) WHERE published_at IS NULL;

CREATE TABLE inbox_events (
    event_id VARCHAR(120) PRIMARY KEY,
    event_type VARCHAR(140) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

-- room_type_id, customer_id, reservation_id and created_by are references owned by other services.
-- They intentionally have no cross-database foreign keys.
