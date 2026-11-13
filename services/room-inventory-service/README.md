# Room Inventory Service

Bounded context cho **một khách sạn**, sở hữu UC06–UC08, UC22 và UC24. Service chạy ở cổng
`8103`, dùng PostgreSQL riêng, Flyway, JWT RS256, RabbitMQ transactional outbox/inbox và optimistic
locking.

## Mô hình nghiệp vụ

- `Floor`, `RoomType`, `Amenity`, `RoomImage`, `Room` là dữ liệu chuẩn của kho phòng.
- `Room.operationalStatus` là trạng thái thời gian thực: `AVAILABLE`, `RESERVED`, `OCCUPIED`,
  `WAITING_CLEANING`, `MAINTENANCE`. Mọi chuyển trạng thái lưu lịch sử bất biến và phát event.
- `InventoryHold` là tồn theo khoảng ngày. Giữ chỗ `HELD` chưa hết hạn và đơn `CONFIRMED` đều
  được trừ khỏi tồn. Lệnh tạo hold khóa bi quan hàng `RoomType` trong một giao dịch để không bán
  vượt số phòng; các cập nhật danh mục/trạng thái dùng `@Version` và `expectedVersion`.
- `MaintenanceBlock` tách khỏi trạng thái hiện tại để truy vấn overlap theo ngày.
- Giá/chính sách vẫn do Pricing Service sở hữu. Inventory chỉ giữ read model
  `room_type_price_snapshots`, nhận `pricing.room-type-price-published.v1` và
  `pricing.room-type-policy-published.v1`. Vì vậy UC06–UC08 có thể lọc/sắp xếp/hiển thị giá mà
  không truy cập database Pricing. Khi chưa có snapshot, API trả `pricingStatus=UNAVAILABLE`.
- Không xóa cứng. Tầng/phòng/loại phòng/tiện nghi/ảnh được ngừng hoạt động. Loại phòng hoặc phòng
  không được ngừng nếu việc đó ảnh hưởng giữ chỗ/đặt phòng tương lai.

## API công khai

- `GET /api/v1/availability/search`: ngày nhận/trả, số khách/phòng, loại phòng, tiện nghi, khoảng
  giá và sort ổn định (`PRICE_ASC`, `PRICE_DESC`, `CAPACITY_ASC`, `CAPACITY_DESC`, `NAME_ASC`).
  Kết quả trừ hold, booking và bảo trì trùng ngày; nếu hết phòng có thể trả ngày gần nhất trong
  14 ngày tiếp theo.
- `GET /api/v1/catalog/floors`
- `GET /api/v1/catalog/amenities`
- `GET /api/v1/catalog/room-types`
- `GET /api/v1/catalog/room-types/{id}`
- `GET /api/v1/catalog/room-types/{id}/availability`: kiểm tra lại tồn và giá theo ngày ngay trên
  trang chi tiết.
- `GET /api/v1/catalog/room-types/{id}/images`

Các API đọc công khai cho phép khách vãng lai và khách đã đăng nhập. Swagger UI ở
`/swagger-ui.html`.

## API quản trị

- `POST|PUT /api/v1/admin/floors[/{id}]`
- `POST|PUT /api/v1/admin/amenities[/{id}]`
- `POST|PUT /api/v1/admin/room-types[/{id}]`
- `POST /api/v1/admin/room-types/{id}/images`, `PUT /api/v1/admin/images/{id}`
- `GET /api/v1/admin/rooms`, `GET /api/v1/admin/hotels/{hotelId}/rooms`
- `POST /api/v1/admin/rooms`, `PUT /api/v1/admin/rooms/{id}`
- `PATCH /api/v1/admin/rooms/{id}/status`
- `GET /api/v1/admin/rooms/{id}/status-history`

Các thao tác cấu hình/tạo/sửa yêu cầu `ADMIN`/`MANAGER`. `RECEPTIONIST` và `HOUSEKEEPER` được
đọc danh sách/lịch sử phòng và cập nhật trạng thái vận hành, nhưng không được tạo hoặc sửa cấu
hình phòng.
Ảnh chỉ nhận metadata `image/jpeg`, `image/png`, `image/webp`, tối đa 10 MiB.
Adapter lưu file/object storage ở lớp ngoài phải kiểm tra bytes thực trước khi gọi API metadata.

Trường tóm tắt chính sách trong response là read model chỉ đọc từ Pricing Service; API quản trị
Inventory không nhận hoặc ghi đè trường này. Event có `policyVersion` cũ hoặc trùng bị bỏ qua.

## API nội bộ cho Reservation/Stay

Mọi endpoint yêu cầu `X-Internal-Key` (secret triển khai, không commit):

- `POST /internal/v1/availability/check`
- `POST /internal/v1/inventory/holds` — bắt buộc `Idempotency-Key`
- `GET /internal/v1/inventory/holds/{id}`
- `POST /internal/v1/inventory/holds/{id}/confirm`
- `DELETE /internal/v1/inventory/holds/{id}`
- `PUT /internal/v1/rooms/{id}/status`

Luồng Reservation chuẩn: kiểm tra → tạo hold → ghi booking → confirm hold. Nếu saga thất bại thì
release; hold chưa confirm tự hết hạn (mặc định 15 phút). Shared internal key là cơ chế local hiện
tại; môi trường production nên bổ sung mTLS/service identity.

## Event phát ra

Envelope tuân ADR-002. Các event chính:

- `inventory.floor.created.v1`, `inventory.floor.updated.v1`
- `inventory.amenity.created.v1`, `inventory.amenity.updated.v1`
- `inventory.room-type.created.v1`, `inventory.room-type.updated.v1`
- `inventory.room-type.image-added.v1`, `inventory.room-type.image-updated.v1`
- `inventory.room.created.v1`, `inventory.room.updated.v1`
- `inventory.room.status-changed.v1`
- `inventory.hold.created.v1`, `inventory.hold.confirmed.v1`,
  `inventory.hold.released.v1`, `inventory.hold.expired.v1`

Publisher chỉ đánh dấu `published_at` sau RabbitMQ publisher confirm và sau khi chắc chắn message
không bị trả về vì không có route. Event Pricing được deduplicate bằng `processed_events`; lỗi cuối
cùng đi vào queue `.dlq`.

## Kiểm thử

```powershell
mvn clean test
```

Unit tests kiểm tra máy trạng thái, optimistic version, tính tồn, giá và chống oversell. Integration
test chạy migration và outbox trên PostgreSQL Testcontainers; test được skip rõ ràng nếu Docker
Desktop chưa chạy.
