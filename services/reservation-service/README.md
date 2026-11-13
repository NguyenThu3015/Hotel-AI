# Reservation Service (UC09-UC13, UC20)

Service sở hữu vòng đời đặt phòng cho một khách sạn. Service không đọc database của Inventory,
Pricing, Payment hoặc Stay; mọi tham chiếu liên service là UUID và snapshot bất biến.

## Luồng nghiệp vụ

1. Tạo booking lấy quote đã version hóa từ Pricing, sau đó tạo hold có hạn tại Inventory. Khi đã có hold,
   Reservation đồng bộ promotion redemption theo `reservationId`; lỗi lưu đơn sẽ giải phóng cả hold và quota.
   Khi đổi/hủy/hết hạn, quota được thay thế hoặc hoàn lại idempotently.
2. Booking cần cọc đi vào `PENDING_PAYMENT`. Payment Service phát `payment.succeeded.v1`;
   Reservation mới quyết định sang `PENDING_CONFIRMATION` hoặc `CONFIRMED` theo policy snapshot.
3. Booking không cần cọc được tự xác nhận, trừ khi policy yêu cầu lễ tân duyệt thủ công.
4. Khách chỉ được đổi trực tiếp booking chưa xác nhận và còn ngoài change cutoff. Trường hợp còn
   lại tạo `ReservationChangeRequest`; lễ tân duyệt hoặc từ chối trong service này.
5. Hủy dùng cancellation-policy snapshot để cố định phí và phát event cho Payment xử lý refund.
6. Scheduler kết thúc hold quá hạn. Event từ Stay đưa booking qua `CHECKED_IN`, `CHECKED_OUT`
   hoặc `NO_SHOW`; Payment và Stay không ghi trực tiếp trạng thái booking.

State machine:

```text
DRAFT/HOLD -> PENDING_PAYMENT -> PENDING_CONFIRMATION -> CONFIRMED
CONFIRMED -> CHECKED_IN -> CHECKED_OUT
pre-confirm -> EXPIRED/REJECTED/CANCELLED
CONFIRMED -> CANCELLED/NO_SHOW
```

## HTTP API

Tất cả command bắt buộc `Idempotency-Key`; update/decision bắt buộc `expectedVersion`.
`/api/v1/bookings` là alias tương thích của `/api/v1/reservations`.

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/reservations` | authenticated customer | UC09 tạo booking online |
| GET | `/api/v1/reservations` | owner | UC20 lịch sử của tôi |
| GET | `/api/v1/reservations/{id}` | owner | UC20 chi tiết của tôi |
| PUT | `/api/v1/reservations/{id}` | owner | UC12 đổi trực tiếp hoặc tạo yêu cầu |
| POST | `/api/v1/reservations/{id}/cancel` | owner | UC13 hủy booking |
| POST | `/api/v1/admin/reservations` | receptionist/manager/admin | UC10 tạo booking phone/walk-in/staff |
| GET | `/api/v1/admin/reservations` | receptionist/manager/admin | UC20 tra cứu/lọc |
| PUT | `/api/v1/admin/reservations/{id}` | receptionist/manager/admin | UC12 thay đổi trực tiếp |
| POST | `/api/v1/admin/reservations/{id}/confirm` | receptionist/manager/admin | UC11 xác nhận thủ công |
| POST | `/api/v1/admin/reservations/{id}/reject` | receptionist/manager/admin | UC11 từ chối |
| POST | `/api/v1/admin/reservations/{id}/cancel` | receptionist/manager/admin | UC13 hủy bởi nhân viên |
| GET | `/api/v1/admin/reservations/{id}/change-requests` | staff | Danh sách yêu cầu đổi |
| POST | `/api/v1/admin/reservations/change-requests/{id}/approve` | staff | Duyệt yêu cầu đổi |
| POST | `/api/v1/admin/reservations/change-requests/{id}/reject` | staff | Từ chối yêu cầu đổi |

Payment và Stay xác minh dữ liệu nguồn qua `GET /internal/reservations/{id}/payment-context` và
`GET /internal/reservations/{id}/stay-context`; client không được tự quyết định số tiền hoặc trạng thái hợp lệ.

Lỗi HTTP dùng RFC 9457 `application/problem+json` và có `correlationId`. Service tự xác minh JWT
RS256/JWKS, audience và RBAC; không tin header danh tính do client tự gửi.

## Hợp đồng nội bộ

Pricing port:

- `POST {PRICING_SERVICE_URL}/internal/v1/pricing/quotes`
- `PUT {PRICING_SERVICE_URL}/internal/v1/pricing/promotion-redemptions/{reservationId}` đồng bộ quota theo
  quote mới; hỗ trợ cờ `compensation` khi phục hồi saga.
- `POST {PRICING_SERVICE_URL}/internal/v1/pricing/promotion-redemptions/{reservationId}/release` hoàn quota
  khi đơn bị hủy, từ chối hoặc hết hạn.
- request: `customerId`, `roomTypeId`, `checkIn`, `checkOut`, `roomCount`, `adults`, `children`,
  `promotionCode`, `channel`.
- response: `quoteId`, `pricingVersion`, `expiresAt`, tiền/currency, `manualConfirmationRequired`,
  `priceBreakdown`, `cancellationPolicy`, `freeCancellationUntil`, `cancellationFeeAfterCutoff`.

Inventory port:

- `POST {INVENTORY_SERVICE_URL}/internal/v1/inventory/holds` tạo hold với `Idempotency-Key` và
  body `{roomTypeId, checkIn, checkOut, quantity, externalReference, ttlMinutes}`.
- `POST {INVENTORY_SERVICE_URL}/internal/v1/inventory/holds/{holdId}/confirm` xác nhận hold cho
  `reservationId`.
- `DELETE {INVENTORY_SERVICE_URL}/internal/v1/inventory/holds/{holdId}` giải phóng.

Inventory hiện chưa có thao tác replace/amend allocation nguyên tử. Khi đổi loại phòng/ngày/số lượng,
Reservation tạo hold mới trước, xác nhận nếu cần rồi mới giải phóng hold cũ; cách này không oversell
nhưng có thể từ chối bảo thủ nếu kho chỉ vừa đủ do hold cũ vẫn đang được tính. Nên bổ sung endpoint
atomic replace ở Inventory trong lát hardening để loại bỏ giới hạn này.

Các request nội bộ gửi `X-Internal-Key`; đây là adapter tạm cho Compose. Có thể thay bằng mTLS hoặc
service identity mà không đổi application port.

## Events

Outbox phát trên topic exchange `hotel.events`:

- `reservation.created.v1`
- `reservation.confirmed.v1`
- `reservation.changed.v1`
- `reservation.change-requested.v1`, `reservation.change-rejected.v1`
- `reservation.cancelled.v1`, `reservation.rejected.v1`
- `reservation.hold-expired.v1`, `reservation.payment-failed.v1`

Inbox loại trùng theo `eventId` và nhận:

- `payment.succeeded.v1`, `payment.failed.v1`, `payment.refund-completed.v1`
- `stay.checked-in.v1`, `stay.checked-out.v1`, `stay.no-show.v1`

Consumer queues đều có DLQ. Publisher chỉ đánh dấu outbox sau broker confirm; delivery là at-least-once,
side effect nghiệp vụ là exactly-once nhờ inbox.

## Database

Flyway `V1__reservation_schema.sql` tạo `reservations`, `reservation_guests`,
`reservation_change_requests`, `idempotency_records`, `outbox_events`, `inbox_events`. Aggregate dùng
`@Version`; unique/index/constraint chặn duplicate reference, hold, pending change và idempotency scope.

## Kiểm thử

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
mvn test
```

Bộ test bao phủ state machine, create/change/idempotency/ownership, JWT RBAC + Problem Details và
mapping persistence JSONB ở H2 PostgreSQL mode. Flyway production dùng PostgreSQL 17.
