-- Hibernate 7 maps Java String to VARCHAR and rejects PostgreSQL bpchar during
-- schema validation even when both declarations have the same maximum length.
ALTER TABLE base_rate_versions
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE price_quotes
    ALTER COLUMN request_hash TYPE VARCHAR(64);

ALTER TABLE idempotency_records
    ALTER COLUMN request_hash TYPE VARCHAR(64);
