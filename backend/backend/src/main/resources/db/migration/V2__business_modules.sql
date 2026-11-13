ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE hotels (
    id BIGSERIAL PRIMARY KEY, name VARCHAR(180) NOT NULL, address VARCHAR(500) NOT NULL,
    description TEXT, phone VARCHAR(30), email VARCHAR(150), check_in_time TIME NOT NULL DEFAULT '14:00',
    check_out_time TIME NOT NULL DEFAULT '12:00', active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE amenities (
    id BIGSERIAL PRIMARY KEY, name VARCHAR(120) NOT NULL UNIQUE, description VARCHAR(500), icon_url VARCHAR(500)
);

CREATE TABLE room_types (
    id BIGSERIAL PRIMARY KEY, hotel_id BIGINT NOT NULL REFERENCES hotels(id), code VARCHAR(40) NOT NULL,
    name VARCHAR(150) NOT NULL, description TEXT, max_adults INT NOT NULL CHECK (max_adults > 0),
    max_children INT NOT NULL DEFAULT 0 CHECK (max_children >= 0), base_price NUMERIC(14,2) NOT NULL CHECK (base_price >= 0),
    size_m2 NUMERIC(8,2), bed_type VARCHAR(80), active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0, UNIQUE(hotel_id, code)
);

CREATE TABLE room_type_amenities (
    room_type_id BIGINT NOT NULL REFERENCES room_types(id), amenity_id BIGINT NOT NULL REFERENCES amenities(id),
    PRIMARY KEY(room_type_id, amenity_id)
);

CREATE TABLE room_images (
    id BIGSERIAL PRIMARY KEY, room_type_id BIGINT NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    url VARCHAR(500) NOT NULL, alt_text VARCHAR(255), display_order INT NOT NULL DEFAULT 0
);

CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY, hotel_id BIGINT NOT NULL REFERENCES hotels(id), room_type_id BIGINT NOT NULL REFERENCES room_types(id),
    room_number VARCHAR(30) NOT NULL, floor VARCHAR(20), status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0, UNIQUE(hotel_id, room_number)
);

CREATE TABLE room_rates (
    id BIGSERIAL PRIMARY KEY, room_type_id BIGINT NOT NULL REFERENCES room_types(id), rate_date DATE NOT NULL,
    price NUMERIC(14,2) NOT NULL CHECK (price >= 0), available_inventory INT CHECK (available_inventory >= 0),
    min_stay INT NOT NULL DEFAULT 1 CHECK (min_stay > 0), closed BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0, UNIQUE(room_type_id, rate_date)
);

CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL,
    discount_type VARCHAR(20) NOT NULL, discount_value NUMERIC(14,2) NOT NULL CHECK (discount_value > 0),
    max_discount NUMERIC(14,2), min_booking_amount NUMERIC(14,2), valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL, usage_limit INT, used_count INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE, version BIGINT NOT NULL DEFAULT 0
);

CREATE SEQUENCE booking_code_seq START 100001;
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY, booking_code VARCHAR(30) NOT NULL UNIQUE,
    user_id BIGINT REFERENCES users(id), hotel_id BIGINT NOT NULL REFERENCES hotels(id),
    check_in_date DATE NOT NULL, check_out_date DATE NOT NULL, adults INT NOT NULL, children INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL, contact_name VARCHAR(150) NOT NULL, contact_email VARCHAR(150) NOT NULL,
    contact_phone VARCHAR(30), special_requests TEXT, promotion_id BIGINT REFERENCES promotions(id),
    room_subtotal NUMERIC(14,2) NOT NULL, service_subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0, tax_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    service_fee NUMERIC(14,2) NOT NULL DEFAULT 0, total_amount NUMERIC(14,2) NOT NULL,
    paid_amount NUMERIC(14,2) NOT NULL DEFAULT 0, hold_expires_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ, cancellation_reason VARCHAR(500), checked_in_at TIMESTAMPTZ, checked_out_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0, CHECK(check_out_date > check_in_date)
);

CREATE TABLE booking_rooms (
    id BIGSERIAL PRIMARY KEY, booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    room_type_id BIGINT NOT NULL REFERENCES room_types(id), room_id BIGINT REFERENCES rooms(id),
    nightly_price NUMERIC(14,2) NOT NULL, guests_count INT NOT NULL, UNIQUE(booking_id, room_id)
);
CREATE INDEX idx_booking_overlap ON bookings(hotel_id, check_in_date, check_out_date, status);
CREATE INDEX idx_booking_rooms_type ON booking_rooms(room_type_id, booking_id);

CREATE TABLE guests (
    id BIGSERIAL PRIMARY KEY, booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    full_name VARCHAR(150) NOT NULL, document_type VARCHAR(30), document_number VARCHAR(80), nationality VARCHAR(80), primary_guest BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE hotel_services (
    id BIGSERIAL PRIMARY KEY, hotel_id BIGINT NOT NULL REFERENCES hotels(id), code VARCHAR(40) NOT NULL,
    name VARCHAR(150) NOT NULL, unit VARCHAR(30) NOT NULL, price NUMERIC(14,2) NOT NULL CHECK(price >= 0), active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE(hotel_id, code)
);

CREATE TABLE service_usages (
    id BIGSERIAL PRIMARY KEY, booking_id BIGINT NOT NULL REFERENCES bookings(id), service_id BIGINT NOT NULL REFERENCES hotel_services(id),
    quantity NUMERIC(10,2) NOT NULL CHECK(quantity > 0), unit_price NUMERIC(14,2) NOT NULL, total_amount NUMERIC(14,2) NOT NULL,
    used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, recorded_by BIGINT REFERENCES users(id), note VARCHAR(500)
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY, booking_id BIGINT NOT NULL REFERENCES bookings(id), idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    provider VARCHAR(30) NOT NULL, provider_transaction_id VARCHAR(120) UNIQUE, type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL, amount NUMERIC(14,2) NOT NULL CHECK(amount > 0), currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    request_payload TEXT, response_payload TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMPTZ, refunded_at TIMESTAMPTZ
);

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY, booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id), invoice_number VARCHAR(40) NOT NULL UNIQUE,
    subtotal NUMERIC(14,2) NOT NULL, discount_amount NUMERIC(14,2) NOT NULL, tax_amount NUMERIC(14,2) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL, issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, pdf_url VARCHAR(500)
);

CREATE TABLE housekeeping_tasks (
    id BIGSERIAL PRIMARY KEY, room_id BIGINT NOT NULL REFERENCES rooms(id), booking_id BIGINT REFERENCES bookings(id),
    assigned_to BIGINT REFERENCES users(id), status VARCHAR(30) NOT NULL DEFAULT 'OPEN', priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    note VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE maintenance_requests (
    id BIGSERIAL PRIMARY KEY, room_id BIGINT NOT NULL REFERENCES rooms(id), reported_by BIGINT REFERENCES users(id),
    assigned_to BIGINT REFERENCES users(id), title VARCHAR(180) NOT NULL, description TEXT, severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ, resolution_note TEXT, version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY, booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id), user_id BIGINT NOT NULL REFERENCES users(id),
    rating INT NOT NULL CHECK(rating BETWEEN 1 AND 5), comment TEXT, status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    hotel_response TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, responded_at TIMESTAMPTZ
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id), type VARCHAR(40) NOT NULL,
    title VARCHAR(180) NOT NULL, content TEXT NOT NULL, read_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES users(id), action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL, entity_id VARCHAR(80), details TEXT, ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO hotels(name,address,description,phone,email) VALUES
('Hotel AI Saigon','1 Nguyễn Huệ, Quận 1, TP.HCM','Khách sạn mẫu phục vụ môi trường phát triển','0280000000','hello@hotelai.local');
INSERT INTO amenities(name,description) VALUES ('WiFi','WiFi tốc độ cao'),('Điều hòa','Điều hòa riêng'),('Bữa sáng','Bữa sáng buffet'),('Hồ bơi','Hồ bơi ngoài trời');
INSERT INTO room_types(hotel_id,code,name,description,max_adults,max_children,base_price,size_m2,bed_type) VALUES
(1,'DELUXE','Deluxe','Phòng Deluxe hướng thành phố',2,1,1200000,32,'KING'),
(1,'FAMILY','Family Suite','Phòng gia đình hai giường',4,2,2200000,55,'2 QUEEN');
INSERT INTO room_type_amenities(room_type_id,amenity_id) VALUES (1,1),(1,2),(2,1),(2,2),(2,3),(2,4);
INSERT INTO rooms(hotel_id,room_type_id,room_number,floor) VALUES
(1,1,'301','3'),(1,1,'302','3'),(1,2,'501','5');
INSERT INTO hotel_services(hotel_id,code,name,unit,price) VALUES
(1,'BREAKFAST','Bữa sáng','SUẤT',180000),(1,'LAUNDRY','Giặt ủi','KG',80000),(1,'EXTRA_BED','Giường phụ','ĐÊM',350000);
