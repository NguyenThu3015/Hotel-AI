-- Hibernate 7 validates Java String fields as VARCHAR. PostgreSQL reports CHAR(n)
-- as bpchar, so align the physical schema without changing the value constraints.
ALTER TABLE reservation_payment_projections
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE pending_stay_folio_projections
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE payment_intents
    ALTER COLUMN currency TYPE VARCHAR(3),
    ALTER COLUMN reservation_snapshot_hash TYPE VARCHAR(64);

ALTER TABLE refund_requests
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE payment_idempotency_records
    ALTER COLUMN request_hash TYPE VARCHAR(64);

ALTER TABLE payment_webhook_deliveries
    ALTER COLUMN raw_payload_sha256 TYPE VARCHAR(64),
    ALTER COLUMN signature_sha256 TYPE VARCHAR(64);
