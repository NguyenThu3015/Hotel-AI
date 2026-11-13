# Pricing Service (UC23, UC26, UC32)

Pricing Service là nguồn dữ liệu chuẩn cho giá phòng, khuyến mãi và chính sách thương mại của
**một khách sạn**. Service chạy ở cổng `8105`, sở hữu `pricing_db` và không truy cập schema của
service khác.

## Quy tắc đã khóa

- Mọi khoảng ngày dùng dạng nửa mở `[from, to)`: `stayTo`/`checkOut` không tính là một đêm.
- Các bản giá, quy tắc và chính sách đã công bố là bất biến. Muốn thay đổi, gửi cùng `ruleKey`,
  `promotionKey` hoặc `policyKey` để tạo phiên bản mới. Quote cũ giữ nguyên snapshot JSON.
- Trong cùng một `ruleKey`, phiên bản lớn nhất đang hiệu lực thắng. Các rule key khác nhau được
  áp dụng theo `priority` tăng dần. Hai rule key khác nhau không được giao nhau nếu cùng priority,
  cùng phạm vi ngày/thời gian, ngày trong tuần và số khách.
- `FIXED_NIGHTLY` thay giá đêm; `MULTIPLIER` nhân giá hiện tại; `ADDITIVE_SURCHARGE` cộng phụ thu.
- Điều kiện số khách của rule tính trên tổng `adults + children` của yêu cầu, không chia theo phòng.
- Tiền dùng scale 2 và `HALF_UP`; currency mặc định là `VND` nhưng có thể đổi bằng biến môi trường.
- Quote có TTL 15 phút mặc định. `validUntil` tự rút ngắn nếu bản giá/rule/promotion/policy hết hiệu
  lực sớm hơn.
- Promotion chỉ bị tiêu quota/ngân sách khi Reservation gọi redeem, không phải khi quote. Một
  `reservationId` chỉ được redeem một lần; quota theo khách yêu cầu quote phải có `customerId`.
- Không seed điều khoản khách sạn vì báo cáo không chỉ định tỷ lệ thực tế. Khi chưa có policy
  `FEES`, thuế/phí là 0; khi chưa có `DEPOSIT`, cọc là không bắt buộc. Response luôn thể hiện rõ các
  giá trị này. Cần tạo policy thật trước khi vận hành production.
- `roomTypeId`, `customerId`, `reservationId`, `createdBy` là UUID do service khác sở hữu; không có
  khóa ngoại xuyên database. Room type được chiếu từ event Inventory hoặc endpoint đồng bộ nội bộ.

## HTTP API

Tất cả lỗi là `application/problem+json`, có `code` và `correlationId`. API quản trị cần JWT RS256
với role `MANAGER` hoặc `ADMIN`. API `/internal/**` cần `X-Internal-Key`. Mọi command ghi dữ liệu cần
`Idempotency-Key` (1–160 ký tự).

| Method | Path | Chức năng |
|---|---|---|
| POST | `/api/v1/admin/pricing/base-rates` | Công bố phiên bản giá cơ bản |
| GET | `/api/v1/admin/pricing/base-rates?roomTypeId=` | Lịch sử bản giá |
| POST | `/api/v1/admin/pricing/rules` | Công bố rule ngày/mùa/số khách |
| GET | `/api/v1/admin/pricing/rules?roomTypeId=` | Lịch sử rule |
| POST | `/api/v1/admin/pricing/simulations` | Mô phỏng quote, không lưu/tiêu quota |
| POST | `/api/v1/admin/pricing/promotions` | Công bố phiên bản promotion |
| GET | `/api/v1/admin/pricing/promotions` | Trạng thái, quota và ngân sách |
| PATCH | `/api/v1/admin/pricing/promotions/{id}/status` | Pause/activate; cần `If-Match` |
| POST | `/api/v1/admin/pricing/policies` | Công bố phiên bản chính sách |
| GET | `/api/v1/admin/pricing/policies` | Phiên bản hiện tại/tương lai/hết hạn |
| POST | `/api/v1/admin/pricing/policies/impact-preview` | Kiểm tra overlap và nghiệp vụ bị ảnh hưởng |
| POST | `/internal/v1/pricing/quotes` | Quote bất biến cho Reservation |
| POST | `/internal/v1/pricing/promotion-redemptions` | Tiêu quota/ngân sách atomically |
| PUT | `/internal/v1/pricing/promotion-redemptions/{reservationId}` | Đồng bộ/đổi promotion theo quote trong saga Reservation |
| POST | `/internal/v1/pricing/promotion-redemptions/{reservationId}/release` | Hoàn quota khi saga bù trừ |
| PUT | `/internal/v1/pricing/room-types/{roomTypeId}` | Đồng bộ projection khi cần bootstrap/repair |

`If-Match` nhận số `entityVersion` trả bởi promotion, ví dụ `If-Match: "3"`.

### Quote request

```json
{
  "roomTypeId": "00000000-0000-0000-0000-000000000101",
  "checkIn": "2026-08-01",
  "checkOut": "2026-08-03",
  "rooms": 2,
  "adults": 3,
  "children": 1,
  "promotionCode": "SUMMER10",
  "customerId": "00000000-0000-0000-0000-000000000201"
}
```

Quote response chứa `nightlyPrices[]` (base version, rule versions, before/after, line total),
`roomSubtotal`, promotion version, discount, phí/thuế, tổng tiền, cọc và toàn bộ policy snapshots.
Reservation phải lưu response/snapshot tại lúc xác nhận đơn theo BR03.

## Schema policy parameters

| `policyType` | Trường chuẩn |
|---|---|
| `CANCELLATION` | `freeCancellationHours >= 0`, `latePenaltyType: PERCENT\|FIXED\|NIGHTS`, `latePenaltyValue >= 0` |
| `NO_SHOW` | `penaltyType: PERCENT\|FIXED\|NIGHTS`, `penaltyValue >= 0` |
| `DEPOSIT` | `required`; nếu true: `depositType: PERCENT\|FIXED`, `depositValue >= 0` |
| `FEES` | `taxPercent`, `serviceFeePercent` trong 0–100 |
| `CHECK_IN_OUT`, `CHILD`, `SURCHARGE` | JSON không rỗng; schema chi tiết sẽ khóa khi chủ khách sạn cung cấp điều khoản thật |

Policy có scope `GLOBAL` (`scopeId=null`) hoặc `ROOM_TYPE` (`scopeId=roomTypeId`). Quote ưu tiên
`ROOM_TYPE`, sau đó mới fallback `GLOBAL`.

## Domain events

Producer dùng envelope chuẩn ADR-002, exchange topic `hotel.events`, routing key bằng event type:

- `pricing.base-rate.published.v1`
- `pricing.rate-rule.published.v1`
- `pricing.promotion.published.v1`
- `pricing.promotion.status-changed.v1`
- `pricing.promotion.redeemed.v1`
- `pricing.promotion.released.v1`
- `pricing.policy.published.v1`
- `pricing.room-type-price-published.v1` (giá hiển thị cơ bản cho read model Inventory)
- `pricing.room-type-policy-published.v1` (tóm tắt chính sách cho read model Inventory)

Consumer inbox/deduplication hỗ trợ:

- `inventory.room-type.created.v1`
- `inventory.room-type.updated.v1`
- `inventory.room-type.status-changed.v1`
- `inventory.room-type.deactivated.v1`

Payload Inventory tối thiểu: `roomTypeId`, `code`, `name`, `active`, `version`. Event lặp được ACK
mà không chạy lại; lỗi parse đi DLQ `pricing.inventory-room-types.dlq.v1`.

## Database

Các nhóm bảng chính:

- version bất biến: `base_rate_versions`, `rate_rule_versions`, `promotion_versions`,
  `policy_versions`;
- snapshot/giao dịch: `price_quotes`, `promotion_redemptions`;
- projection: `room_type_references`;
- độ tin cậy: `idempotency_records`, `outbox_events`, `inbox_events`.

Quota/ngân sách promotion khóa bản ghi khi redeem và đồng thời có `@Version`. Các thay đổi trạng
thái promotion yêu cầu optimistic version qua `If-Match`.

## Kiểm thử

```powershell
mvn test
```

Bộ test hiện có kiểm tra thứ tự rule và breakdown đa phòng/đa đêm, thiếu base rate, điều kiện quota
khách, outlier cần xác nhận, xung đột priority, schema policy, giới hạn ngân sách/quota,
idempotency và hợp đồng truy vấn JPA (exclusive end + scope precedence).
