UPDATE stays s
SET last_saga_error = NULL,
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE s.last_saga_error IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM stay_operations o
      WHERE o.stay_id = s.id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM stay_operations o
      WHERE o.stay_id = s.id
        AND o.status <> 'COMPLETED'
  );
