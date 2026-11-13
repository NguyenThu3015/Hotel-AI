# Payment Service (UC14)

Payment Service sở hữu giao dịch thanh toán, đặt cọc, hoàn tiền và biên nhận cho một khách sạn. Service
chạy ở cổng `8106`, sở hữu `payment_db` và chỉ nhận dữ liệu đặt phòng qua event; không đọc database của
Reservation hay Billing.

## Luồng nghiệp vụ

1. Event `reservation.*.v1` tạo/cập nhật projection gồm khách hàng, email liên hệ, trạng thái và số tiền.
2. Khách chọn khoản thanh toán (`DEPOSIT`, `FULL`, `BALANCE`) và nhận VietQR Casso có số tiền cùng mã
   `PAY-...` duy nhất. `MOMO`/`SANDBOX` vẫn có ở backend; lễ tân có thể ghi nhận `CASH`.
3. Service khóa theo `reservationId`, kiểm tra số còn phải trả, tạo mã giao dịch duy nhất và lưu snapshot
   giá/phiên bản đặt phòng trước khi gọi provider.
4. Casso Webhook V2 được kiểm chữ ký HMAC-SHA512, tài khoản nhận, mã thanh toán, số tiền và loại tiền.
   Mỗi giao dịch được lưu theo khóa duy nhất; webhook lặp không ghi nhận tiền hoặc phát event lần hai.
5. Outbox phát `payment.succeeded.v1`/`payment.failed.v1`; Reservation dùng inbox để cập nhật tiền đã trả
   và trạng thái đơn.
6. Giao dịch thành công có biên nhận JSON/HTML và có thể yêu cầu gửi email qua
   `notification.requested.v1`.

Không có endpoint hoặc cột dữ liệu thẻ. Frontend chỉ hiển thị VietQR/thông tin chuyển khoản; hệ thống
không yêu cầu hay lưu thông tin đăng nhập ngân hàng.

## HTTP API

Mọi command do người dùng/nhân viên gọi cần `Idempotency-Key` (8–160 ký tự an toàn). Lỗi dùng
`application/problem+json` và `X-Correlation-Id`.

| Method | Endpoint | Quyền | Chức năng |
|---|---|---|---|
| POST | `/api/v1/payments/intents` | owner/staff | Tạo giao dịch Casso/MoMo/Sandbox |
| GET | `/api/v1/payments?reservationId=` | owner/staff | Lịch sử giao dịch |
| GET | `/api/v1/payments/{id}` | owner/staff | Chi tiết và URL checkout |
| GET | `/api/v1/payments/{id}/bank-transfer` | owner/staff | VietQR và hướng dẫn chuyển khoản Casso |
| POST | `/api/v1/payments/{id}/cancel` | owner/staff | Hủy giao dịch đang chờ |
| POST | `/api/v1/payments/{id}/refunds` | owner/staff | Yêu cầu hoàn tiền |
| GET | `/api/v1/payments/{id}/receipt` | owner/staff | Xem biên nhận |
| GET | `/api/v1/payments/{id}/receipt/download` | owner/staff | Tải biên nhận HTML |
| POST | `/api/v1/payments/{id}/receipt/email` | owner/staff | Gửi biên nhận qua Notification |
| POST | `/api/v1/payments/sandbox/{id}/complete` | owner/staff | Giả lập kết quả local |
| POST | `/api/v1/admin/payments/cash` | receptionist/manager/admin | Ghi nhận tiền mặt |
| GET | `/api/v1/admin/payments?reservationId=` | staff | Tra cứu giao dịch theo đơn |
| GET | `/api/v1/admin/refunds?paymentId=` | staff | Danh sách hoàn tiền |
| POST | `/api/v1/admin/refunds/{id}/approve` | staff | Duyệt và gửi provider |
| POST | `/api/v1/admin/refunds/{id}/reject` | staff | Từ chối |
| POST | `/api/v1/admin/refunds/{id}/complete` | staff | Hoàn tất Cash thủ công |
| POST | `/api/v1/payments/webhooks/momo` | public provider | Nhận MoMo IPN đã ký |
| POST | `/api/v1/payments/webhooks/casso` | public provider | Nhận Casso Webhook V2 đã ký |

## Casso/VietQR adapter

- Nội dung chuyển khoản có dạng `HOTELAI PAY-<payment UUID không dấu gạch>`; không dùng mã booking dễ đoán.
- VietQR Quick Link khóa số tiền, nội dung và tên chủ tài khoản từ cấu hình server.
- Chữ ký `X-Casso-Signature` được kiểm HMAC-SHA512 trên JSON chuẩn hóa theo Webhook V2.
- Chỉ giao dịch tiền vào, đúng tài khoản, đúng mã, đúng số tiền nguyên VND mới được ghi nhận.
- Transaction ID và event ID chống xử lý lặp; raw webhook/hash chữ ký được lưu phục vụ kiểm toán.
- Giao dịch không liên quan được trả `200` để Casso Strict Mode không retry vô ích.
- Hoàn tiền qua Casso là chuyển khoản thủ công sau khi nhân viên phê duyệt; không tự động trừ ngân hàng.

## MoMo adapter

Bộ khung bám API AIO v2:

- tạo giao dịch: `/v2/gateway/api/create`, `captureWallet`, `autoCapture=true`;
- đối soát giao dịch chưa rõ kết quả: `/v2/gateway/api/query`;
- hoàn tiền: `/v2/gateway/api/refund`;
- đối soát hoàn tiền đang xử lý: `/v2/gateway/api/refund/query`;
- amount nguyên dương, tiền tệ `VND`;
- chữ ký HMAC-SHA256 theo thứ tự trường của MoMo;
- `requestId` ổn định theo payment/refund để provider idempotency;
- timeout đọc 30 giây cho API provider;
- `resultCode=0` và `9000` được coi là thành công cho payment một bước;
- hoàn tiền `0` hoàn tất ngay, `7000/7002` giữ `PROCESSING` và scheduler truy vấn lại theo phút.

Adapter mặc định `enabled=false`. Khi toàn bộ luồng nội bộ hoàn thành sẽ cần `partnerCode`, `accessKey`,
`secretKey`, callback HTTPS công khai và thông tin môi trường thật để bật.

## Độ tin cậy

- PostgreSQL advisory lock + unique constraint bảo vệ idempotency và giao dịch đang chờ.
- Optimistic/pessimistic lock bảo vệ callback, hoàn tiền và scheduler hết hạn.
- Transactional outbox + RabbitMQ publisher confirm/mandatory; consumer dùng transactional inbox.
- Raw webhook và hash chữ ký được lưu để kiểm toán, nhưng secret không được ghi log/lưu DB.
- Giao dịch chờ hết hạn mặc định sau 15 phút; dữ liệu idempotency dọn sau 24 giờ.

## Kiểm thử

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
mvn test
```

Các biến bắt buộc: `CASSO_ENABLED=true`, `CASSO_WEBHOOK_SECRET`, `CASSO_BANK_ID`,
`CASSO_ACCOUNT_NUMBER`, `CASSO_ACCOUNT_NAME`. Webhook phải trỏ đến Gateway qua HTTPS công khai.

Bộ test bao phủ state machine, số tiền theo projection, ownership, Cash, chữ ký/callback Casso và MoMo,
RBAC/Problem Details và mapping JPA.
