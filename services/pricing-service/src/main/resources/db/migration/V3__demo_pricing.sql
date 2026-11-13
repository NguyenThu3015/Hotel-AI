-- The demo inventory is inserted directly by room-inventory-service/V2, so it does
-- not emit the room-type-created event that normally builds this Pricing projection.
-- Seed the matching reference and a broad demo rate to keep a clean local startup
-- immediately usable for availability search and end-to-end booking.
INSERT INTO room_type_references (
    id, code, name, active, source_version, updated_at, version
) VALUES (
    '30000000-0000-0000-0000-000000000001',
    'DELUXE',
    'Deluxe',
    TRUE,
    1,
    now(),
    0
);

INSERT INTO base_rate_versions (
    id, room_type_id, version_no, currency, amount,
    stay_from, stay_to, booking_effective_from, booking_effective_to,
    outlier_confirmed, created_by, created_at,
    inventory_snapshot_published_at, lock_version
) VALUES (
    '31000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    1,
    'VND',
    1200000.00,
    DATE '2020-01-01',
    DATE '2100-01-01',
    TIMESTAMPTZ '2020-01-01 00:00:00+00',
    NULL,
    FALSE,
    '00000000-0000-0000-0000-000000000001',
    now(),
    now(),
    0
);

INSERT INTO outbox_events (
    id, aggregate_type, aggregate_id, event_type, correlation_id,
    schema_version, payload, occurred_at, published_at,
    publish_attempts, last_error, version
) VALUES (
    '32000000-0000-0000-0000-000000000001',
    'BaseRate',
    '31000000-0000-0000-0000-000000000001',
    'pricing.room-type-price-published.v1',
    '32000000-0000-0000-0000-000000000002',
    1,
    '{
      "roomTypeId": "30000000-0000-0000-0000-000000000001",
      "nightlyPrice": 1200000.00,
      "currency": "VND",
      "taxRate": 0,
      "serviceFee": 0,
      "pricingVersion": 1,
      "effectiveAt": "2020-01-01T00:00:00Z"
    }'::jsonb,
    now(),
    NULL,
    0,
    NULL,
    0
);
