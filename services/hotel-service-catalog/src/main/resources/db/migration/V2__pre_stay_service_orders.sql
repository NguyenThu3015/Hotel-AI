CREATE TABLE pre_stay_service_orders (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    service_id UUID NOT NULL REFERENCES hotel_services(id),
    service_code VARCHAR(50) NOT NULL,
    service_name VARCHAR(160) NOT NULL,
    quantity NUMERIC(12,3) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    tax_rate NUMERIC(7,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_pre_stay_order_quantity CHECK (quantity > 0),
    CONSTRAINT ck_pre_stay_order_status CHECK (status IN ('REQUESTED','CONFIRMED','FULFILLED','CANCELLED')),
    CONSTRAINT ck_pre_stay_order_amounts CHECK (unit_price >= 0 AND tax_rate >= 0 AND tax_rate <= 1)
);
CREATE INDEX ix_pre_stay_orders_reservation ON pre_stay_service_orders(reservation_id, scheduled_at);
CREATE INDEX ix_pre_stay_orders_customer ON pre_stay_service_orders(customer_id, created_at DESC);
