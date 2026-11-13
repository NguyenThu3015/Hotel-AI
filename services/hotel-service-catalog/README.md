# Hotel Service Catalog (UC17, UC25)

Bounded context for the hotel service catalog and the services charged during an open stay.

- UC25 versions price and tax terms, validates service schedules, and deactivates catalog
  entries instead of deleting financial history.
- UC17 records, adjusts, performs, and cancels usages with an immutable financial revision
  for every command.
- Unit price and tax are captured at the usage time. Cancelling a performed service requires
  `MANAGER` or `ADMIN`.
- Stay state is an event projection. Financial deltas are emitted through a transactional
  outbox for Stay Service to add to the folio.
- All financial write endpoints require `Idempotency-Key`.

The service runs on port `8108`; HTTP traffic is expected to enter through API Gateway.
