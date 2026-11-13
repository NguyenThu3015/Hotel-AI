# Hotel AI System

Hệ thống quản lý và đặt phòng cho **một khách sạn**, triển khai bằng 12 microservice nghiệp vụ, 5 microservice AI và một API Gateway. Frontend chỉ gọi API Gateway; cấu hình microservice đích không còn route fallback sang `legacy-backend`.

## Thành phần chính

- `platform/api-gateway`: Spring Cloud Gateway, xác thực JWT, rate limit và correlation ID.
- `services/identity-service`: UC01–UC04, UC28, UC29.
- `services/customer-service`: UC05, UC27.
- `services/room-inventory-service`: UC06–UC08, UC22, UC24, hồ sơ khách sạn, buồng phòng và bảo trì.
- `services/reservation-service`: UC09–UC13, UC20.
- `services/pricing-service`: UC23, UC26, UC32.
- `services/payment-service`: UC14; chuyển khoản VietQR/Casso có webhook đối soát, idempotency, Cash/Sandbox và khung MoMo dự phòng.
- `services/stay-service`: UC15, UC16, UC18.
- `services/hotel-service-catalog`: UC17, UC25.
- `services/billing-service`: UC19; công nợ, hóa đơn, PDF/email và chứng từ điều chỉnh.
- `services/review-service`: UC21; đánh giá xác thực, kiểm duyệt, phản hồi và lưu kết quả AI UC38.
- `services/notification-service`: UC31; SMTP/Mailpit và eSMS Zalo ZNS sandbox. SMS Brandname chưa bật.
- `services/reporting-audit-service`: UC30, UC33.
- `ai-service`: một mã nguồn dùng chung, được triển khai thành năm tiến trình độc lập:
  - `ai-conversation-service`: UC34, UC40, Gemini, hội thoại nhiều lượt, RAG và tool calling.
  - `ai-recommendation-service`: UC35.
  - `ai-forecasting-service`: UC36 và phát hiện bất thường.
  - `ai-revenue-service`: UC37, giá sàn/trần, phiên bản và phê duyệt của con người.
  - `ai-customer-analytics-service`: UC38, UC39.
- `frontend`: Next.js/React/TypeScript; giao diện khách và quản trị.

Chi tiết hiện trạng từng use case nằm tại [documents/COVERAGE.md](documents/COVERAGE.md).

## Chạy bằng Docker

Yêu cầu: Docker Desktop đã ở trạng thái **Engine running**. Chỉ việc thấy cửa sổ Docker Desktop mở chưa đảm bảo Docker Engine đã sẵn sàng; lệnh sau phải trả kết quả nhanh:

```powershell
docker info --format '{{.ServerVersion}}'
```

Tạo cấu hình local lần đầu và điền `GEMINI_API_KEY`:

```powershell
Copy-Item infrastructure/.env.example infrastructure/.env
```

Cấu hình AI tối thiểu:

```env
AI_PROVIDER=gemini
GEMINI_API_KEY=your-key
GEMINI_CHAT_MODEL=gemini-3.1-flash-lite
GEMINI_EMBEDDING_MODEL=gemini-embedding-2
```

Cấu hình eSMS Zalo ZNS cho đồ án (chỉ sandbox, không gửi tin thật và không tính phí):

```env
ZALO_PROVIDER=esms-zns-sandbox
ZALO_API_URL=https://rest.esms.vn/MainService.svc/json/SendZaloMessage_V5_post/
ZALO_API_KEY=your-esms-api-key
ZALO_SECRET_KEY=your-esms-secret-key
ZALO_OA_ID=4097311281936189049
ZALO_TEMPLATE_OTP_ID=205644
ZALO_TEMPLATE_DATA_KEY=otp
ZALO_SANDBOX=true
```

Email giao dịch dùng SMTP thật (Gmail hoặc SMTP provider khác), không cần Brandname:

```env
EMAIL_ADAPTER_MODE=smtp
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-gmail@gmail.com
SMTP_PASSWORD=app-password-16-ky-tu
SMTP_AUTH=true
SMTP_STARTTLS=true
EMAIL_FROM=your-gmail@gmail.com
EMAIL_FALLBACK_TO_MAILPIT=true
```

Email được tạo tự động qua outbox cho các mốc đặt phòng (tiếp nhận, xác nhận, sửa, hủy, hết hạn), thanh toán/hoàn tiền, check-in, đổi phòng, gia hạn, trả phòng, hóa đơn/điều chỉnh hóa đơn và phản hồi đánh giá. Đây là email giao dịch; SMS/Zalo vẫn là kênh riêng và không bị thay thế. Khi dùng Gmail, kiểm tra cả Inbox và Spam của `SMTP_USERNAME`. Nếu SMTP chính không kết nối được trong Docker và `EMAIL_FALLBACK_TO_MAILPIT=true`, email được giữ trong Mailpit tại http://localhost:8025 để OTP không bị mất. Healthcheck không gọi SMTP bên ngoài và các kết nối SMTP có timeout hữu hạn.

Thanh toán mặc định trên frontend dùng chuyển khoản ngân hàng cá nhân qua VietQR và Casso Flow. Điền thông tin tài khoản đã liên kết với Casso vào `.env` rồi bật adapter:

```env
CASSO_ENABLED=true
CASSO_WEBHOOK_SECRET=key-bao-mat-webhook-v2
CASSO_BANK_ID=ma-bin-hoac-ten-ngan-hang
CASSO_ACCOUNT_NUMBER=so-tai-khoan
CASSO_ACCOUNT_NAME=TEN CHU TAI KHOAN
CASSO_TRANSFER_PREFIX=HOTELAI
```

Trong Casso Flow, tạo tích hợp **Webhook V2** với URL HTTPS công khai:

```text
https://ten-mien-cua-ban/api/v1/payments/webhooks/casso
```

Chọn đúng tài khoản ngân hàng nhận tiền, bật Strict Mode và dùng cùng `CASSO_WEBHOOK_SECRET`. Khi chạy local, Gateway `localhost:8081` phải được mở ra Internet bằng tunnel HTTPS; Casso không gọi được URL `localhost`. Frontend tạo VietQR kèm số tiền và mã `PAY-...`; webhook chỉ ghi nhận thành công khi chữ ký, tài khoản nhận, mã thanh toán, số tiền và tiền tệ đều khớp. Giao dịch lặp được bỏ qua theo transaction ID. Hoàn tiền Casso hiện là thao tác chuyển khoản thủ công có phê duyệt.

Để chạy Casso từ máy local mà không công khai toàn bộ Gateway, dự án có profile ngrok đi qua một Nginx ingress chỉ cho phép `POST /api/v1/payments/webhooks/casso`. Tạo tài khoản ngrok miễn phí, lấy authtoken và điền `NGROK_AUTHTOKEN` trong `.env`, sau đó chạy:

```powershell
./scripts/casso/start-casso-tunnel.ps1
```

Script sẽ in URL HTTPS đầy đủ để dán vào ô **Webhook URL** của Casso. Giữ container ngrok chạy trong lúc kiểm thử. Khi không dùng nữa:

```powershell
./scripts/casso/stop-casso-tunnel.ps1
```

SMS Brandname không được bật. Sau khi đổi cấu hình Zalo, chỉ cần build và tạo lại Notification Service:

```powershell
docker compose -f infrastructure/docker-compose.microservices.yml build notification-service
docker compose -f infrastructure/docker-compose.microservices.yml up -d --no-deps notification-service
```

Máy Windows 16 GB RAM dùng cấu hình `C:\Users\ADMIN\.wslconfig` để dành 12 luồng CPU, 8 GB RAM và 2 GB swap cho WSL/Docker. Sau khi thay đổi file này cần thoát Docker Desktop, chạy `wsl --shutdown`, rồi mở Docker Desktop lại.

Build tuần tự khi stack đang dừng, sau đó khởi động theo từng tầng:

```powershell
docker compose -f infrastructure/docker-compose.microservices.yml config --quiet
./scripts/docker/stop-local.ps1
./scripts/docker/build-local.ps1
./scripts/docker/start-local.ps1
```

Không chạy `docker compose up --build` cho toàn bộ stack trên máy local: Maven/Next build và hơn 20 container cold-start cùng lúc có thể làm Docker Engine nghẽn. Các script trên build tuần tự, giới hạn song song và chờ từng nhóm healthy. `stop-local.ps1` không xóa volume.

Docker Desktop lưu `docker_data.vhdx` trên ổ C theo mặc định. Luôn giữ ít nhất 10 GB trống trên ổ chứa VHD; con số `Disk limit` trong Docker Desktop là giới hạn logic, không phải dung lượng trống thật của ổ C. Khi ổ C đầy, BuildKit/overlayfs có thể chuyển read-only và làm container dừng đột ngột.

Xem mức dùng tài nguyên thực tế:

```powershell
./scripts/docker/resource-report.ps1
```

Java service mặc định bị giới hạn `0.5 CPU / 512 MiB`, AI service `0.35 CPU / 256 MiB`; RabbitMQ chỉ dùng tối đa 1 CPU. Có thể chỉnh các giá trị trong `infrastructure/.env` theo mẫu `.env.example`.

Observability được tắt mặc định để tiết kiệm tài nguyên. Chỉ bật khi cần Grafana/Prometheus/Jaeger:

```powershell
./scripts/docker/start-local.ps1 -IncludeObservability
```

Xem 200 dòng log gần nhất (lệnh tự kết thúc):

```powershell
docker compose -f infrastructure/docker-compose.microservices.yml logs --tail 200
```

Theo dõi log liên tục thì thêm `-f`; nhấn `Ctrl+C` chỉ dừng theo dõi log, không dừng container.

Các địa chỉ local:

- Web: http://localhost:3000
- API Gateway: http://localhost:8081
- Gateway health: http://localhost:8081/actuator/health
- Mailpit: http://localhost:8025
- RabbitMQ Management: http://localhost:15672
- Jaeger (profile observability): http://localhost:16686
- Prometheus (profile observability): http://localhost:9090
- Grafana (profile observability): http://localhost:3001

Tài khoản quản trị mặc định cho môi trường local:

```text
Email: admin@hotelai.local
Mật khẩu: Admin@12345
```

Hãy đổi `BOOTSTRAP_ADMIN_EMAIL` và `BOOTSTRAP_ADMIN_PASSWORD` khi triển khai ngoài máy local.

## Kiểm thử thực tế

Sau khi tất cả container cần thiết đã `healthy`, chạy bộ kiểm thử tổng hợp:

```powershell
# Chỉ cần chạy một lần trên máy mới
cd frontend
npx playwright install chromium ffmpeg
cd ..

./scripts/e2e/run-all.ps1
```

Bộ chạy này kiểm tra đăng ký/OTP/hồ sơ/thu hồi token thật qua Gateway và Mailpit, sau đó chạy Playwright cho giao diện cùng các route UC01–UC40. Báo cáo lỗi, ảnh, video và trace của Playwright được lưu trong `frontend/playwright-report` và `frontend/test-results`.

Chạy riêng từng phần:

```powershell
./scripts/e2e/slice1-identity-customer-notification-mailpit.ps1

# Payment Casso: tạo booking test, QR, webhook ký hợp lệ và kiểm tra dedupe
./scripts/e2e/casso-payment-smoke.ps1

# Luồng lễ tân thật: booking tại quầy, tiền mặt, check-in, dịch vụ,
# check-out, WAITING_CLEANING và hoàn tất housekeeping
./scripts/e2e/front-desk-stay-smoke.ps1

# Một kiểm tra API/runtime riêng cho từng UC01-UC40
./scripts/e2e/uc01-uc40-contract.ps1

cd frontend
npm run test:e2e
```

## Kiểm tra mã nguồn

```powershell
# Frontend
cd frontend
npm run typecheck
npm run lint
npm run build
npx playwright test --list

# AI
cd ../ai-service
.\.venv\Scripts\python.exe -m ruff format --check .
.\.venv\Scripts\python.exe -m ruff check .
.\.venv\Scripts\python.exe -m pytest -q

# Một Java service (JDK 21)
cd ../services/review-service
mvn test
```

## Phần cần credential hoặc kiểm định thêm

- Casso cần tài khoản ngân hàng liên kết, Key bảo mật Webhook V2 và URL Gateway HTTPS công khai. MoMo được giữ làm adapter dự phòng nhưng không còn là luồng frontend mặc định.
- Zalo ZNS đã tích hợp eSMS sandbox. SMS Brandname vẫn dùng adapter log/mock và chưa gửi thật.
- SMTP production cần tài khoản SMTP thật; local dùng Mailpit.
- UC35, UC36 và UC39 đã có luồng chức năng/baseline nhưng vẫn cần dữ liệu lịch sử đủ lớn để huấn luyện, hiệu chỉnh và đo chất lượng mô hình trước khi dùng cho quyết định kinh doanh.

Các quyết định kiến trúc nằm trong `documents/architecture`; hợp đồng sự kiện nằm tại [contracts/asyncapi.yaml](contracts/asyncapi.yaml).

## Dữ liệu mẫu và kiểm tra đủ UC01–UC40

Tạo dữ liệu đặt phòng thực tế qua API Gateway (không chèn thẳng vào database):

```powershell
# Tạo 7 loại phòng mới, giá/tiện nghi/ảnh và đúng 50 phòng vật lý mới
./scripts/seed/room-inventory-data.ps1

# Tạo booking và đơn dịch vụ mẫu
./scripts/seed/sample-data.ps1 -Count 50 -CancelEvery 10 -WithServices

# Nạp lịch sử 18 tháng và kho tri thức RAG cho AI: khách hàng, booking theo mùa,
# thanh toán, dịch vụ, đánh giá/cảm xúc, rủi ro hủy-no-show và giới hạn giá AI
./scripts/seed/ai-demo-data.ps1
```

Script tự chọn ngày/phòng còn trống, gắn marker `HOTELAI-SAMPLE-DATA-V1`, tạo một số yêu cầu dịch vụ đặt trước và hủy có kiểm soát. Chạy lại sẽ tạo thêm một lô dữ liệu mới; không xóa dữ liệu hiện có.

`ai-demo-data.ps1` dùng marker `HOTELAI_AI_DEMO_V2`, có thể chạy lại an toàn và không giữ tồn phòng thật. Script đồng thời nạp tài liệu RAG tiếng Việt có phân quyền khách/nhân viên, gọi API backtest dự báo, chấm điểm rủi ro booking và phát hiện bất thường để xác nhận dữ liệu vừa nạp thực sự được các microservice AI sử dụng.

Kiểm tra hợp đồng từng UC và trạng thái route:

```powershell
./scripts/e2e/uc01-uc40-contract.ps1
```

Các endpoint vận hành AI bổ sung để theo dõi chất lượng là `/api/v1/ai/recommendations/model-info`, `/api/v1/ai/forecast/backtest` và `/api/v1/ai/risks/bookings/model-info`.
