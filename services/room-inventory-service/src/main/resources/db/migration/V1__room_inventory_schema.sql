CREATE TABLE floors (
    id UUID PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    level_number INTEGER NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE amenities (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    icon_url VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE room_types (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    max_adults INTEGER NOT NULL,
    max_children INTEGER NOT NULL,
    max_occupancy INTEGER NOT NULL,
    size_sqm NUMERIC(7,2),
    bed_description VARCHAR(500),
    policy_summary VARCHAR(2000),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_room_type_capacity CHECK (
        max_adults > 0 AND max_children >= 0 AND max_occupancy > 0
        AND max_occupancy >= max_adults
    ),
    CONSTRAINT ck_room_type_size CHECK (size_sqm IS NULL OR size_sqm > 0)
);

CREATE TABLE room_type_amenities (
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    amenity_id UUID NOT NULL REFERENCES amenities(id),
    PRIMARY KEY (room_type_id, amenity_id)
);

CREATE TABLE room_images (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    url VARCHAR(1000) NOT NULL,
    alt_text VARCHAR(300) NOT NULL,
    media_type VARCHAR(80) NOT NULL,
    byte_size BIGINT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_room_image_order CHECK (display_order >= 0),
    CONSTRAINT ck_room_image_size CHECK (byte_size > 0 AND byte_size <= 10485760),
    CONSTRAINT ck_room_image_media CHECK (media_type IN ('image/jpeg', 'image/png', 'image/webp'))
);
CREATE INDEX idx_room_images_type_order ON room_images(room_type_id, display_order, id);

CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    room_number VARCHAR(30) NOT NULL UNIQUE,
    floor_id UUID NOT NULL REFERENCES floors(id),
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    operational_status VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    current_stay_id UUID,
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_room_status CHECK (operational_status IN (
        'AVAILABLE', 'RESERVED', 'OCCUPIED', 'WAITING_CLEANING', 'MAINTENANCE'
    ))
);
CREATE INDEX idx_rooms_type_active ON rooms(room_type_id, active);
CREATE INDEX idx_rooms_floor_status ON rooms(floor_id, operational_status);

CREATE TABLE room_status_history (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    previous_status VARCHAR(30) NOT NULL,
    new_status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_by UUID,
    source VARCHAR(50) NOT NULL,
    correlation_id UUID NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_room_status_history_room_time ON room_status_history(room_id, changed_at DESC);

CREATE TABLE maintenance_blocks (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    start_date DATE NOT NULL,
    end_date DATE,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_maintenance_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_maintenance_status CHECK (status IN ('OPEN', 'RESOLVED', 'CANCELLED'))
);
CREATE INDEX idx_maintenance_overlap ON maintenance_blocks(room_id, start_date, end_date, status);

CREATE TABLE inventory_holds (
    id UUID PRIMARY KEY,
    room_type_id UUID NOT NULL REFERENCES room_types(id),
    reservation_id UUID,
    external_reference VARCHAR(120),
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_hold_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT ck_hold_quantity CHECK (quantity > 0),
    CONSTRAINT ck_hold_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_hold_expiry CHECK (
        (status = 'HELD' AND expires_at IS NOT NULL) OR status <> 'HELD'
    )
);
CREATE INDEX idx_holds_overlap ON inventory_holds(room_type_id, check_in_date, check_out_date, status, expires_at);
CREATE UNIQUE INDEX ux_holds_reservation_confirmed
    ON inventory_holds(reservation_id)
    WHERE reservation_id IS NOT NULL AND status = 'CONFIRMED';

-- Non-authoritative read models owned by Pricing Service and updated through events/internal API.
CREATE TABLE room_type_price_snapshots (
    room_type_id UUID PRIMARY KEY REFERENCES room_types(id),
    nightly_price NUMERIC(14,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    tax_rate NUMERIC(7,4) NOT NULL DEFAULT 0,
    service_fee NUMERIC(14,2) NOT NULL DEFAULT 0,
    pricing_version BIGINT NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_price_snapshot_values CHECK (
        nightly_price >= 0 AND tax_rate >= 0 AND service_fee >= 0
    )
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(150) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    correlation_id UUID NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(occurred_at) WHERE published_at IS NULL;
