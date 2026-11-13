WITH latest_success AS (
    SELECT DISTINCT ON (aggregate_id)
           aggregate_id,
           payload,
           occurred_at
    FROM payment_outbox_events
    WHERE event_type = 'payment.succeeded.v1'
    ORDER BY aggregate_id, occurred_at DESC
)
UPDATE payment_intents p
SET status = 'SUCCEEDED',
    provider_transaction_id = COALESCE(
        p.provider_transaction_id,
        NULLIF(e.payload ->> 'providerReference', '')
    ),
    completed_at = COALESCE(
        p.completed_at,
        NULLIF(e.payload ->> 'completedAt', '')::TIMESTAMPTZ,
        e.occurred_at
    ),
    updated_at = GREATEST(p.updated_at, e.occurred_at),
    version = p.version + 1
FROM latest_success e
WHERE p.id = e.aggregate_id
  AND p.status IN ('CREATED', 'PENDING');
