ALTER TABLE bookings
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'WEB';

CREATE INDEX idx_bookings_hotel_source ON bookings(hotel_id, source, created_at);
CREATE INDEX idx_bookings_user_created ON bookings(user_id, created_at);
CREATE INDEX idx_payments_status_created ON payments(status, created_at);

ALTER TABLE payments
    ADD COLUMN refunded_amount NUMERIC(14,2) NOT NULL DEFAULT 0;

CREATE TABLE refund_requests (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    amount NUMERIC(14,2) NOT NULL CHECK(amount > 0),
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);
