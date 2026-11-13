# Reporting & Audit Service

Microservice triển khai UC30 và UC33 cho mô hình một khách sạn.

- Chiếu dữ liệu báo cáo từ `hotel.events` vào database riêng; không đọc trực tiếp
  database của service nghiệp vụ.
- Tính booking, hủy, doanh thu, công suất, ADR, RevPAR và doanh thu dịch vụ theo
  ngày, loại phòng, nguồn đặt và dịch vụ.
- So sánh kỳ hiện tại với kỳ liền trước, trả định nghĩa chỉ số và trạng thái không
  có dữ liệu.
- Xuất Excel/PDF đồng bộ hoặc tạo tác vụ nền có idempotency.
- Lưu audit chỉ-thêm, che dữ liệu nhạy cảm và nối hash SHA-256 để phát hiện sửa đổi.
- Mọi lần xem/xuất audit và báo cáo đều được ghi audit.

Service chạy nội bộ ở cổng `8112`; HTTP công khai chỉ đi qua API Gateway.

## Cấu hình chính

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `REPORTING_AUDIT_DB_URL` | `jdbc:postgresql://postgres:5432/reporting_audit_db` | Database riêng |
| `HOTEL_CURRENCY` | `VND` | Tiền tệ báo cáo |
| `HOTEL_TIME_ZONE` | `Asia/Ho_Chi_Minh` | Múi giờ nghiệp vụ |
| `REPORT_MAX_RANGE_DAYS` | `366` | Khoảng thời gian tối đa |
| `AUDIT_RETENTION_DAYS` | `365` | Chính sách lưu audit |
| `INTERNAL_SERVICE_KEY` | `local-internal-key` | Khóa API ghi audit nội bộ |

Chi tiết API và quy tắc chỉ số nằm tại
`documents/api/SLICE-06-REPORTING-AUDIT-NOTIFICATION.md`.
