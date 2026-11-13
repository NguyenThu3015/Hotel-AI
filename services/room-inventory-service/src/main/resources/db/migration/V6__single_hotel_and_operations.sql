-- V6 follows the existing V1-V5 inventory history.
CREATE TABLE hotel_profiles (
    id SMALLINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    description TEXT,
    phone VARCHAR(30),
    email VARCHAR(320),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_single_hotel CHECK (id = 1)
);

INSERT INTO hotel_profiles(id, name, address, description, phone, email)
VALUES (
    1,
    'HotelAI',
    'Ho Chi Minh City, Vietnam',
    'Khách sạn thông minh phục vụ một cơ sở.',
    '0900000000',
    'contact@hotelai.local'
);

CREATE TABLE housekeeping_tasks (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id),
    assigned_to UUID,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_housekeeping_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_housekeeping_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE UNIQUE INDEX uq_housekeeping_active_room
    ON housekeeping_tasks(room_id)
    WHERE status IN ('OPEN', 'IN_PROGRESS');
CREATE INDEX ix_housekeeping_status_time
    ON housekeeping_tasks(status, created_at);

ALTER TABLE maintenance_blocks
    ADD COLUMN title VARCHAR(160),
    ADD COLUMN description TEXT,
    ADD COLUMN severity VARCHAR(20),
    ADD COLUMN resolution TEXT,
    ADD COLUMN created_by UUID;

UPDATE maintenance_blocks
SET title = 'Bảo trì phòng',
    description = reason,
    severity = 'MEDIUM'
WHERE title IS NULL;

ALTER TABLE maintenance_blocks
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN severity SET NOT NULL,
    ADD CONSTRAINT ck_maintenance_severity
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));
