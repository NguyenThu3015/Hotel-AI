-- Keep the database type consistent with the Java String mapping used by
-- Hibernate schema validation.
ALTER TABLE room_type_price_snapshots
    ALTER COLUMN currency TYPE VARCHAR(3);
