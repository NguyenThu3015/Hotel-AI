# Billing Service (UC19)

Billing builds an immutable invoice snapshot from three event projections:

- Reservation: room subtotal, discount, tax, buyer contact and pricing breakdown.
- Stay: final charge total and every signed folio line.
- Payment: successful payment and completed refund ledger entries.

Draft creation fails when any source total differs by more than `0.01`. Issue is allowed only
after Stay is `CLOSED`, the balance is zero, and the source hash is unchanged since preview.
Invoice numbers use a locked yearly sequence (`INV-YYYY-NNNNNN`).

Issued financial fields and lines are immutable. Manager adjustments and cancellations create a
separate numbered correction document. PDF download is generated from the locked snapshot; email
delivery is requested asynchronously through Notification Service so a delivery failure never
invalidates the invoice.

All write operations require `Idempotency-Key`. The service runs on port `8109` behind API Gateway.
