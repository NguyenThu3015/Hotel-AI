-- Hibernate 7 validates Java String fields as VARCHAR rather than PostgreSQL
-- bpchar, so align both fixed-length text columns with the entity mappings.
ALTER TABLE reservations
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE idempotency_records
    ALTER COLUMN request_hash TYPE VARCHAR(64);
