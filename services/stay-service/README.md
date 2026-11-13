# Stay Service

Bounded context for UC15 (check-in), UC16 (room change/extension) and UC18
(check-out).

Key guarantees:

- Guest identity document numbers are encrypted with AES-256-GCM and API
  responses expose only the last four characters.
- Check-in and room transitions use idempotent room-ownership commands in Room
  Inventory Service.
- Room change occupies the new room before ending the old assignment.
- Extension creates confirmed inventory holds for every active room type before
  changing the checkout date.
- Checkout remains in `CHECKOUT_PAYMENT_PENDING` while the folio balance is not
  zero, unless a manager/admin records an auditable override.
- Local outbox/inbox tables and retryable saga state make RabbitMQ and
  inter-service failures recoverable.

Real deployments must replace `STAY_DOCUMENT_ENCRYPTION_KEY` with a secret
Base64 value that decodes to exactly 32 random bytes.
