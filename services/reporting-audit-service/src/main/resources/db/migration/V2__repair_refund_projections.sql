-- Earlier versions classified payment.refund-*.v1 as payment lifecycle events.
-- Rebuild the affected read models from the append-only domain event facts.
WITH latest_refunds AS (
    SELECT DISTINCT ON (payload ->> 'refundId')
        payload,
        occurred_at
    FROM domain_event_facts
    WHERE (event_type LIKE 'payment.refund-%'
            OR event_type LIKE 'payment.refund.%'
            OR event_type LIKE 'refund.%')
      AND payload ? 'refundId'
    ORDER BY payload ->> 'refundId', occurred_at DESC
)
INSERT INTO refund_facts(
    refund_id, payment_id, reservation_id, amount, currency,
    status, occurred_at, updated_at)
SELECT
    (payload ->> 'refundId')::UUID,
    NULLIF(payload ->> 'paymentId', '')::UUID,
    NULLIF(payload ->> 'reservationId', '')::UUID,
    COALESCE((payload ->> 'amount')::NUMERIC, 0),
    payload ->> 'currency',
    COALESCE(payload ->> 'status', 'UNKNOWN'),
    occurred_at,
    occurred_at
FROM latest_refunds
ON CONFLICT (refund_id) DO UPDATE SET
    payment_id = COALESCE(EXCLUDED.payment_id, refund_facts.payment_id),
    reservation_id = COALESCE(EXCLUDED.reservation_id, refund_facts.reservation_id),
    amount = EXCLUDED.amount,
    currency = COALESCE(EXCLUDED.currency, refund_facts.currency),
    status = EXCLUDED.status,
    occurred_at = EXCLUDED.occurred_at,
    updated_at = EXCLUDED.updated_at;

WITH latest_payments AS (
    SELECT DISTINCT ON (payload ->> 'paymentId')
        payload,
        occurred_at
    FROM domain_event_facts
    WHERE event_type LIKE 'payment.%'
      AND event_type NOT LIKE 'payment.refund-%'
      AND event_type NOT LIKE 'payment.refund.%'
      AND payload ? 'paymentId'
    ORDER BY payload ->> 'paymentId', occurred_at DESC
)
UPDATE payment_facts payment
SET status = COALESCE(latest.payload ->> 'status', payment.status),
    occurred_at = latest.occurred_at,
    updated_at = GREATEST(payment.updated_at, latest.occurred_at)
FROM latest_payments latest
WHERE payment.payment_id = (latest.payload ->> 'paymentId')::UUID;

UPDATE payment_facts payment
SET refunded_amount = refunds.total
FROM (
    SELECT payment_id, SUM(amount) AS total
    FROM refund_facts
    WHERE status IN ('COMPLETED', 'SUCCEEDED')
    GROUP BY payment_id
) refunds
WHERE payment.payment_id = refunds.payment_id;
