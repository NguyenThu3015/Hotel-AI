# Review Service

Bounded context cho UC21:

- Đồng bộ Stay và Customer/Identity bằng RabbitMQ; không đọc database service khác.
- Chỉ khách sở hữu kỳ lưu trú `CLOSED` mới được gửi đánh giá.
- Một đánh giá xác thực cho mỗi kỳ lưu trú.
- Chủ đề chuẩn: Room, Cleanliness, Service, Staff, Location, Value,
  Amenities và Other.
- Nội dung sạch tự công bố; PII, nội dung lặp, markup nguy hiểm hoặc từ cấm
  được giữ ở `PENDING_MODERATION`.
- Email, số điện thoại và số thẻ được thay bằng marker trước khi lưu.
- Manager/Admin có thể publish, hide hoặc reject với lịch sử quyết định.
- Một phản hồi chính thức cho mỗi đánh giá; mọi lần sửa tạo revision bất biến.
- Transactional outbox, consumer inbox và `Idempotency-Key` bảo vệ retry.

Service chạy cổng nội bộ `8110`; HTTP public đi qua API Gateway.
