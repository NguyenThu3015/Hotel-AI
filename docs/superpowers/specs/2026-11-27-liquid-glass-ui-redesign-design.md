# Thiết Kế Giao Diện HotelAI — Adaptive Dual-Surface Liquid Glass

**Ngày:** 2026-11-27  
**Phạm vi:** Toàn bộ Frontend (`frontend/src/`) — Cổng đặt phòng khách hàng & Trung tâm điều hành nội bộ (`/admin`, 9 phân hệ)  
**Hướng thẩm mỹ:** Adaptive Dual-Surface Liquid Glass (Kính quang học phân tầng thích ứng, giàu chiều sâu & chuyển động sống động)

---

## 1. Mục đích & Lý do (Intent & Why)

Hệ thống `HotelAI` đã hoàn thiện đầy đủ 40 Use Cases (12 microservice nghiệp vụ, 5 microservice AI, API Gateway), nhưng giao diện hiện tại tại `frontend/src/` gặp ba hạn chế lớn về trải nghiệm thực tế:
1. **Mật độ thông tin nặng nề ở Cổng khách (`/`):** Phần Hero xếp chồng 6 lớp huy hiệu; khung tìm phòng dùng lưới 2 cột gồm các ô `<input type="number">` thô chiếm nhiều chiều dọc, đẩy danh sách phòng trống (`#suites`) xuống rất sâu. Ngoài ra, bộ tính `lowestPrice` trên Hero chưa lọc các loại phòng kiểm thử giá thấp (`1.000 ₫`).
2. **Thiếu chiều sâu quang học và nhịp điệu chuyển động:** Giao diện hiện mới dừng ở chuyển dịch `translateY(-2px)` đơn giản khi rê chuột, chưa có chất liệu kính khúc xạ ánh sáng, chiều sâu phân lớp hay hiệu ứng xuất hiện mượt mà theo tương tác.
3. **Đơn điệu ở Trung tâm điều hành (`/admin`) và các trang phụ (`/rooms`, `/bookings`, `/login`, `/profile`):** Tất cả đang dồn chung vào một file `globals.css` 6.455 dòng khó bảo trì, chưa phân tách rõ chất liệu điều khiển nổi (navigation/controls) với mặt phẳng đọc dữ liệu cường độ cao của nhân viên.

Mục tiêu của đợt thiết kế lại là nâng tầm đồng bộ cả hai mảng thành một trải nghiệm **Liquid Glass hiện đại giàu chiều sâu**, hiệu ứng sống động như ứng dụng gốc cao cấp nhưng thao tác cực gọn, dễ nhìn và tuyệt đối không phá vỡ hợp đồng API hay bộ kiểm thử E2E hiện có.

---

## 2. Mục tiêu, Phi mục tiêu & Điều cần tránh (Goals, Non-Goals & Anti-Goals)

### Mục tiêu (Goals)
- **Hệ vật liệu Liquid Glass chuẩn quang học:** Xây dựng hệ thống biến CSS phân tầng cho lớp kính điều hướng/điều khiển (`backdrop-filter: blur() saturate()`, viền phản xạ ánh sáng `specular rim` bằng `inset box-shadow` + `linear-gradient` border mask, ánh sáng môi trường `ambient aurora mesh`) kết hợp lớp mặt phẳng nội dung sắc nét đạt chuẩn tương phản WCAG AA (`>= 4.5:1`).
- **Tối ưu mạnh luồng đặt phòng khách (`/`):**
  - Thu gọn Hero thoáng đãng, sang trọng, lọc bỏ phòng kiểm thử (`!isPresentableRoomType` hoặc giá `< 100.000 ₫`) khỏi chỉ số giá thấp nhất trên Hero.
  - Biến khung tìm phòng thành **Floating Liquid Glass Booking Bar** gọn gàng (hiển thị trực tiếp Điểm đến, Nhận phòng, Trả phòng, Bộ chọn/stepper Khách & Số phòng, nút `Tìm phòng trống`) giúp danh sách phòng hiển thị gần tầm mắt hơn hẳn.
  - Làm mới toàn diện thẻ Điểm đến 3 miền, thẻ Hạng phòng (`RoomCard`), thanh Quản gia AI gợi ý, Modal đặt phòng, cửa sổ `ChatWidget` nổi, cùng các trang `/rooms`, `/bookings`, `/login`, `/profile` và hộp thoại/toast toàn cục (`ui-provider.tsx`).
- **Hiện đại hóa Trung tâm điều hành `/admin` (9 phân hệ):**
  - Thiết kế không gian **Frosted Pearl & Deep Ocean Cockpit**: Sidebar kính nổi phân tách tinh tế, Topbar kính trong suốt cố định gọn gàng, thẻ KPI có thanh trực quan sinh động, bảng dữ liệu Lễ tân / Kho phòng / Hóa đơn / Duyệt giá AI rõ nét, trạng thái badge đồng bộ và biểu mẫu dễ quét mắt.
- **Hiệu ứng sống động nhưng mượt nhẹ (60fps):**
  - Ánh sáng khúc xạ theo con trỏ chuột (`pointer spotlight` qua biến CSS `--mx`, `--my` điều tiết bằng `requestAnimationFrame`) trên các tấm kính chủ đạo.
  - Hiệu ứng xuất hiện phân tầng khi cuộn (`IntersectionObserver` scroll-reveal stagger) với fallback an toàn hiển thị mặc định khi tắt JS hoặc bật `prefers-reduced-motion`.
  - Chuyển đổi mượt khi lọc vùng miền, chuyển tab trải nghiệm, mở/đóng `ChatWidget` và nhảy số mượt cho các chỉ số thống kê.
- **Tách cấu trúc CSS dễ bảo trì:** Tách hệ thống style mới thành các module CSS rõ ràng trong `frontend/src/styles/` được import có trật tự từ `globals.css`, thay vì tiếp tục phình to một file đơn lẻ.

### Phi mục tiêu (Non-Goals)
- Không thay đổi bất kỳ API backend, schema cơ sở dữ liệu, cấu hình Gateway hay logic nghiệp vụ đặt phòng/thanh toán/lưu trú/AI.
- Không cài thêm các thư viện UI/animation bên ngoài nặng nề (như GSAP thương mại, Framer Motion hay Tailwind) để giữ nguyên kiến trúc dependency tối giản của `frontend/package.json` và tốc độ build Docker nhanh.

### Điều cần tránh (Anti-Goals — thất bại dù chạy đúng kỹ thuật)
- **Lạm dụng kính mờ lên bảng dữ liệu chữ nhỏ:** Không đặt bảng nhiều cột của `/admin` hoặc chữ mô tả nhỏ trực tiếp trên nền ảnh nhiễu có độ tương phản thấp gây lóa mắt.
- **Ẩn điều khiển tìm kiếm sau nhiều bước bấm:** Không giấu ô `Nhận phòng`, `Trả phòng` hoặc nút `Tìm phòng trống` vào hộp ẩn khiến người dùng (và kiểm thử tự động) phải bấm mở mới thấy.
- **Hiệu ứng rườm rà gây giật lag:** Không chạy `backdrop-filter: blur()` đồng thời trên hàng chục dòng bảng hoặc chạy animation làm trễ thao tác nhập liệu của nhân viên lễ tân.

---

## 3. Ràng buộc Kỹ thuật & Kiểm thử (Constraints)

1. **Tương thích tuyệt đối bộ kiểm thử Playwright E2E (`frontend/e2e/`):**
   - Giữ nguyên mọi `aria-label`, `role`, `placeholder`, nhãn `<label>` và văn bản nút/heading được kiểm tra trong `uc01-uc40.spec.ts`, `regression-workflows.spec.ts`, `auth-customer-journey.spec.ts` và `admin-ai-quality.spec.ts`:
     - Trang chủ (`/`): hai ô `input[type="date"]` có nhãn `Nhận phòng` và `Trả phòng` hiển thị trực tiếp ngay khi tải trang ở cả `390x844`, `768x1024`, `1366x768`; nút `aria-label="Tìm phòng trống"`; heading `Kết quả theo thời gian thực`; nút `/Gợi ý bằng AI/`; nhãn `/AI phù hợp/`; class `.room-card`, `.booking-modal`; các trường đặt phòng `Loại giấy tờ`, `Số giấy tờ`, `Quốc tịch`, `Yêu cầu đặc biệt`, nút `Xác nhận giữ phòng`.
     - Chat Widget: nút mở có `aria-label` chứa `Hỏi trợ lý AI`, ô nhập có placeholder `Tôi cần phòng cho 2 người...`, nút `Gửi`, tin nhắn trả lời khớp selector `.chat-messages .assistant`.
     - Xác thực (`/login`) & Hồ sơ (`/profile`) & Kỳ nghỉ (`/bookings`): giữ nguyên các heading (`Chào mừng trở lại`, `Tạo tài khoản`, `Khôi phục mật khẩu`, `Hồ sơ cá nhân`), các nhãn input (`Email`, `Mật khẩu`, `Họ và tên`, `Số điện thoại`, `Điện thoại`, `Mã xác minh gồm 6 số`, `Mật khẩu hiện tại`, `Mật khẩu mới`, `Nhập lại mật khẩu mới`), class `.booking-list article`, vùng `Thông tin chuyển khoản`, ảnh alt `Mã VietQR thanh toán đặt phòng`, `.payment-code-callout strong`, và `.app-toast.toast-success` / `.app-toast.toast-error`.
     - Quản trị (`/admin`): chữ `Trung tâm điều hành`; tọa độ đỉnh của `.admin-workspace` luôn `< 100px` (`workspacePosition.y < 100` — tức thanh Header trên `/admin` phải giữ chiều cao `<= 76px` và không chèn banner đẩy `.admin-workspace` xuống); không hiển thị link `Kỳ nghỉ của tôi` cho tài khoản nhân viên; giữ nguyên tên 9 nút điều hướng và `<h1>` tương ứng trong `.admin-workspace`, `.walkin-panel summary`, `.checkin-modal`, `.guest-form`, `.checkin-room-grid select`, cùng cấu trúc `<section>` chứa `<h2>Giá theo ngày</h2>` ở phân hệ Kho phòng & giá.
   - **Quy tắc Accessibility bắt buộc (`admin-ai-quality.spec.ts:165-180`):**
     - Không tràn ngang (`documentElement.scrollWidth - clientWidth <= 1`) ở mọi khung nhìn `390px`, `768px`, `1366px`, `1440px`.
     - **Mọi thẻ `<button>` trên trang đều phải có văn bản hiển thị hoặc `aria-label` hợp lệ** (`button:not([aria-label])` rỗng text = `0`).
2. **An toàn khi build Docker (`frontend/Dockerfile`):**
   - Nạp Google Fonts (`Playfair Display` + `Plus Jakarta Sans` + `Be Vietnam Pro` hỗ trợ đầy đủ tiếng Việt) qua thẻ `<link>` chuẩn hoặc `@import` kèm font dự phòng hệ thống, **không** dùng `next/font/google` để tránh lỗi mạng lúc `npm run build` bên trong container Alpine.
3. **Thích ứng Reduced Motion & Reduced Transparency:**
   - Tôn trọng `@media (prefers-reduced-motion: reduce)` và `@media (prefers-reduced-transparency: reduce)`: tắt ngay hiệu ứng dịch chuyển/stagger và tăng độ đục nền kính khi hệ điều hành yêu cầu.
4. **Quy trình chạy thực tế khi phát triển:**
   - Giữ nguyên 22 container backend/AI/Gateway chạy trên Docker (`localhost:8081`); sau khi hoàn tất và kiểm chứng mã nguồn (`typecheck`, `lint`, `build`, Playwright E2E), cập nhật lại image container `frontend` (hoặc chạy dev server) để `http://localhost:3000` luôn phục vụ giao diện mới nhất.

---

## 4. Các Phương án Đã Cân nhắc (Approaches)

1. **Phương án 1 — Adaptive Dual-Surface Liquid Glass + Tái cấu trúc luồng thao tác (Đã chọn):**
   - Dùng vật liệu Liquid Glass đa tầng (ánh sáng môi trường, viền phản sáng, kính mờ thích ứng) cho hệ thống điều hướng, thanh đặt phòng nổi, thẻ điểm nhấn, trợ lý AI và khung điều khiển; đồng thời đặt nội dung đọc chi tiết và bảng nghiệp vụ `/admin` trên bề mặt kính sáng độ tương phản cao, kết hợp tinh gọn lại bố cục Hero + Thanh tìm phòng.
   - *Đánh đổi:* Phạm vi chỉnh sửa rộng hơn thay CSS thuần túy, nhưng giải quyết trọn vẹn cả thẩm mỹ, hiệu ứng sống động lẫn tính dễ dùng.
2. **Phương án 2 — Full Dark Obsidian Liquid Glass:**
   - Nền tối toàn phần cho cả Khách và `/admin`. Bị loại vì làm giảm khả năng đọc nhanh các bảng dữ liệu nhiều cột và form dài ở khu vực vận hành nội bộ.
3. **Phương án 3 — CSS-Only Glass Reskin:**
   - Chỉ sửa `globals.css`, giữ nguyên JSX cũ. Bị loại vì không giải quyết được sự nặng nề của Hero 6 tầng và khung tìm phòng 2 cột đẩy kết quả xuống sâu.

---

## 5. Kiến trúc Thiết kế Chi tiết (Detailed Design)

### 5.1. Hệ Vật liệu, Bảng màu & Chữ viết (Liquid Glass Design System)
*(Các chi tiết thông số token cụ thể do người triển khai tinh chỉnh — Builder's call)*

- **Ba lớp chiều sâu (3-Layer Spatial Depth):**
  1. **Ambient Canvas (Lớp nền khí quyển):**
     - *Cổng Khách:* Chuyển tiếp nhịp nhàng giữa **Deep Lagoon Midnight (`#041916` → `#072822`)** ở vùng Hero/Trải nghiệm với các vệt sáng khuếch tán mềm (`cyan-emerald` `rgba(24, 182, 148, 0.22)` & `champagne gold` `rgba(232, 192, 114, 0.18)`) và nền **Luminous Pearl (`#F4F7F6` → `#FAF8F4`)** có ánh cầu vồng cực nhẹ ở vùng danh sách phòng để thông tin giá/tiện nghi sáng rõ.
     - *Trung tâm `/admin`:* Nền **Cool Frosted Mist (`#EEF3F2` → `#F6F8F8`)** điểm ánh sáng dịu phía góc trên, giúp 9 phân hệ quản trị mang cảm giác không gian kính hiện đại nhưng sạch và dịu mắt suốt ca trực.
  2. **Optical Liquid Glass Chrome (Lớp kính điều hướng & điều khiển nổi):**
     - Áp dụng cho `Header`, `Floating Booking Bar`, `Preset Chips`, `ChatWidget`, `Toast/Dialog`, `Booking Modal`, `Admin Sidebar`, `Admin Workspace Topbar`:
     - Kính sáng: `background: linear-gradient(135deg, rgba(255,255,255,0.78), rgba(255,255,255,0.56))`, `backdrop-filter: blur(24px) saturate(180%)`, viền kép phản sáng `border: 1px solid rgba(255,255,255,0.72)` kèm `box-shadow: inset 0 1px 0 rgba(255,255,255,0.92), 0 20px 50px rgba(6, 28, 24, 0.10)`.
     - Kính tối (trên nền Hero/Footer/Card ảnh): `background: linear-gradient(145deg, rgba(10, 38, 33, 0.62), rgba(5, 22, 19, 0.44))`, `backdrop-filter: blur(22px) saturate(165%)`, viền `1px solid rgba(255, 255, 255, 0.18)`, `inset 0 1px 0 rgba(255, 255, 255, 0.28)`.
  3. **Crisp Content Surfaces (Lớp thẻ dữ liệu & bảng nghiệp vụ):**
     - Thẻ phòng, thẻ kỳ nghỉ, form đăng nhập và bảng `/admin` dùng nền kính đục cao (`rgba(255, 255, 255, 0.90)`–`0.96`), bo góc mượt (`18px`–`26px`), chữ chính `#07201B` (tương phản `> 13:1`), chữ phụ `#436059` (tương phản `> 5.5:1`), điểm nhấn chính `Emerald Teal` (`#0B7A64` → `#129B7F`) và điểm nhấn thượng lưu `Champagne Gold` (`#C8963E` → `#E8C27A`).
- **Hệ chữ (Typography):**
  - Display / Editorial Heading: `'Playfair Display'`, Georgia, serif — dùng cho tiêu đề lớn cổng khách với nét thanh nét đậm sang trọng, hỗ trợ trọn vẹn dấu tiếng Việt.
  - UI & Data Sans: `'Plus Jakarta Sans'`, `'Be Vietnam Pro'`, system-ui, sans-serif — dùng cho toàn bộ điều khiển, nhãn, bảng số liệu (`font-variant-numeric: tabular-nums` cho mọi con số tiền tệ, ngày tháng, KPI).

### 5.2. Tái cấu trúc Cổng Đặt Phòng Khách (`Guest Portal`)

1. **Floating Capsule Header (`components/header.tsx`):**
   - Trên cổng khách, Header biến thành thanh kính lơ lửng tinh tế khi cuộn trang (`backdrop-filter: blur(24px)`), các mục điều hướng có pill kính trượt/sáng mượt ở trạng thái đang chọn, nút đăng nhập/tài khoản bo tròn dạng viên nang sang trọng.
2. **Hero Tinh Gọn & Sống Động (`app/page.tsx`):**
   - Lược bỏ sự chồng chéo rối mắt: giữ **1 dải trạng thái mảnh tinh tế** (Điểm đến đang tâm điểm + khí hậu), **Tiêu đề chính giàu cảm xúc**, **2 nút hành động rõ ràng** và **Thẻ Kính Quang Học Tóm Tắt Thực Tế** bên phải (hiển thị số phòng sẵn sàng thực tế, điểm đến tiêu biểu chuyển đổi trực tiếp ngay trên thẻ, và mức giá khởi điểm hợp lệ).
   - **Quy tắc hiển thị mức giá khởi điểm ("Chỉ từ…") — quyết định của người thiết kế (Builder's call):** Giữ nguyên hàm `isPresentableRoomType` trong `src/lib/vi.ts` và danh sách phòng ở `#suites` / `/rooms` để không ảnh hưởng phòng test chuyển khoản thật giá nhỏ (ví dụ `1.000 ₫` cho VietQR/Casso), nhưng trên các huy hiệu quảng bá **"Chỉ từ…"** ở Hero và thẻ Điểm đến Bento thì chỉ tính trên các mức giá thương mại `>= 100.000 ₫` (nếu không có thì hiển thị nhãn đặc quyền thay thế).
   - Thêm vầng sáng môi trường chuyển động chậm (`ambient aurora orbs`) phía sau lớp ảnh khu nghỉ dưỡng tạo chiều sâu không gian.
3. **Thanh Đặt Phòng Kính Nổi 1 Hàng (`#search` trong `app/page.tsx`) & Thứ tự Phân khu Trang chủ:**
   - **Thứ tự phân khu trang chủ (Builder's call để tối ưu tính dễ sử dụng):** `Hero` → `Thanh Đặt Phòng Kính Nổi (#search)` → `Danh sách Phòng Thời Gian Thực & Quản Gia AI (#suites)` → `Dải Bảo Chứng Dịch Vụ (4 cam kết gọn)` → `Bento 10 Điểm Đến 3 Miền` → `Nhịp Điệu Trải Nghiệm & Dịch Vụ Đính Kèm` → `Đánh Giá Thượng Khách` → `CTA Cuối Trang`. Việc đưa `#suites` lên ngay sát dưới `#search` giúp khách vừa bấm tìm phòng là thấy ngay kết quả trong tầm mắt; khi khách chọn một thẻ ở khu Bento Điểm Đến bên dưới, trang lọc điểm đến và cuộn mượt ngược lên `#suites`.
   - Nằm nổi đè nhẹ lên chân Hero (`margin-top` âm có kiểm soát trên màn hình lớn, xếp chồng gọn trên mobile), gom trọn trên **một dải ngang đồng nhất** trên desktop (`Điểm đến` · `Nhận phòng` · `Trả phòng` · cụm điều khiển `Người lớn / Trẻ em / Số phòng` kèm nút `−/+` tiện bấm ngay bên cạnh ô số · nút CTA nổi bật `Tìm phòng phù hợp` có `aria-label="Tìm phòng trống"`).
   - Các chip gợi ý kịch bản nhanh (`Cặp đôi · 1 đêm`, `Cuối tuần · 2 đêm`, `Gia đình · 3 đêm`, `Biệt thự VIP · 4 đêm`) và thanh mở rộng `Bộ lọc Đặc quyền & Cá nhân hóa` được thiết kế dạng pill kính mỏng gọn ngay phía trên/dưới thanh tìm kiếm.
4. **Danh sách Phòng Thời Gian Thực (`#suites`) & Thanh Quản Gia AI:**
   - Thanh `AI Bespoke Concierge` có hiệu ứng viền ánh kim chuyển sắc nhẹ (`shimmer border`) mời gọi bấm gợi ý; khi có điểm phù hợp AI (`AI phù hợp XX%`), thẻ phòng nổi bật với huy hiệu kính ánh vàng kim và lý do gợi ý dễ đọc.
   - Mỗi `.room-card` được thiết kế dạng **Horizontal/Bento Luxury Suite Card** thoáng mắt: ảnh phòng có hiệu ứng zoom mượt + ánh sáng lướt nhẹ khi hover, chỉ giữ tối đa 3 huy hiệu quan trọng nhất trên ảnh, nhóm tiện nghi dạng chip gọn, cột giá tách bạch rõ tổng tiền trọn gói + thuế phí + nút `Chọn phòng này`.
5. **Bento Điểm Đến 3 Miền, Trải Nghiệm & Dịch Vụ Đính Kèm:**
   - Lưới Bento 10 điểm đến có bộ lọc tab vùng miền dạng thanh kính trượt (`Segmented Glass Control`), thẻ điểm đến phản hồi ánh sáng theo vị trí chuột (`--mx`, `--my`).
   - Phần nhịp điệu trải nghiệm và dịch vụ đặt trước rút gọn khoảng đệm thừa, thẻ chọn dịch vụ đính kèm hiển thị trạng thái "Đã chọn" bằng viền kính ngọc lục bảo rõ nét.
6. **Các trang `/rooms`, `/bookings`, `/login`, `/profile`, `ChatWidget` & `UiProvider`:**
   - `/login`: Bố cục 2 cột kính nghệ thuật (bên trái là câu chuyện thương hiệu trên nền ảnh/aurora kính tối, bên phải là biểu mẫu kính sáng có hiệu ứng chuyển mượt giữa Đăng nhập / Đăng ký / Quên mật khẩu / Nhập OTP 6 số).
   - `/bookings`: Mỗi đơn đặt phòng là một thẻ kính phân khu rõ trạng thái màu, tiến độ thanh toán, khung quét mã **VietQR** nổi bật như thẻ ngân hàng cao cấp, dễ sao chép mã và đổi lịch.
   - `/rooms` & `/profile`: Đồng bộ banner tiêu đề kính, lưới hạng phòng có bộ lọc nhanh theo vùng miền/tình trạng trống ngay trên trang `/rooms` (cải thiện tính tiện dụng), và trang hồ sơ chia thẻ thông tin/bảo mật/thông báo thoáng sạch.
   - `ChatWidget`: Nút mở dạng viên nang kính nổi có nhịp thở nhẹ (`pulse halo`), cửa sổ hội thoại kính mờ hai lớp với các chip gợi ý câu hỏi nhanh dễ chạm.

### 5.3. Tái cấu trúc Trung tâm Điều hành Nội bộ (`/admin` — 9 Phân hệ)

1. **Khung Cockpit Chuẩn `< 100px` Top Offset (`app/admin/page.tsx` & `components/header.tsx`):**
   - Khi ở route `/admin`, Header giữ chiều cao gọn `68px` (đảm bảo `.admin-workspace` luôn bắt đầu ở `y = 68px < 100px`), kết hợp cùng `.admin-sidebar` dạng tấm kính mờ nổi bên trái có chỉ báo trạng thái trực tuyến, nhóm phân hệ rõ ràng và hiệu ứng highlight kính trượt mượt khi chuyển phân hệ.
2. **Đồng bộ Toàn bộ 9 Phân hệ Quản trị (`admin-overview`, `admin-front-desk`, `admin-management`, `admin-operations`, `admin-customers`, `admin-access-control`, `admin-policies`, `admin-ai-operations`, `admin-stay-actions`):**
   - **Thẻ KPI & Dự báo AI (`AdminOverview`, `AdminAiOperations`):** Thẻ kính có thanh tiến độ/biểu đồ cột mini mượt mà, phân màu mức độ rủi ro (`LOW`, `MEDIUM`, `HIGH`) rõ nét, khung chat trợ lý nội bộ đồng bộ chất liệu kính với cổng khách.
   - **Bàn Lễ Tân & Lưu Trú (`AdminFrontDesk`, `AdminStayActions`):** Khung đặt phòng khách vãng lai (`.walkin-panel`), bộ lọc trạng thái/điểm đến, bảng danh sách đặt phòng có hàng hover mượt, huy hiệu trạng thái dễ phân biệt từ xa, và modal nhận phòng (`.checkin-modal`) trình bày rõ từng phòng – từng khách.
   - **Kho Phòng, Giá, Vận Hành, Khách Hàng, Phân Quyền & Chính Sách:** Chuẩn hóa toàn bộ `.panel`, `details/summary`, bảng dữ liệu (`<table>` có khung cuộn ngang mượt trên màn hình nhỏ mà không làm tràn trang), ô nhập liệu, nút bấm và hộp thoại xác nhận trên cùng hệ thống Liquid Glass.

### 5.4. Kiến trúc Tệp & Hệ thống Chuyển động (Code & Motion Architecture)
*(Chi tiết tổ chức file nội bộ do người triển khai quyết định — Builder's call)*

- Tạo component/hook nhẹ `frontend/src/components/liquid-motion.tsx` (Client Component không phụ thuộc ngoài):
  - Quản lý biến CSS `--pointer-x`, `--pointer-y` trên các thẻ kính có gắn thuộc tính `data-glass-spotlight` bằng `pointermove` + `requestAnimationFrame` (chỉ chạy trên thiết bị có con trỏ `pointer: fine`).
  - Kích hoạt hiệu ứng xuất hiện mượt cho các khối `[data-reveal]` khi đi vào khung nhìn bằng `IntersectionObserver` (mặc định phần tử vẫn hiển thị đầy đủ nếu trình duyệt giảm chuyển động hoặc chưa chạy observer để không bao giờ bị ẩn trắng trang).
- Tổ chức CSS modular tại `frontend/src/styles/`:
  - `tokens.css`: Biến màu, độ trong suốt kính, bóng đổ khúc xạ, bo góc, chữ viết, easing curves (`cubic-bezier(0.22, 1, 0.36, 1)`).
  - `liquid-glass.css`: Lớp tiện ích vật liệu kính, nền aurora, hiệu ứng spotlight, scroll-reveal, toast & dialog, header/footer, chat widget.
  - `guest-portal.css`: Hero, thanh đặt phòng nổi, Bento điểm đến, thẻ phòng, modal đặt phòng, `/rooms`, `/bookings`, `/login`, `/profile`.
  - `admin-cockpit.css`: Bố cục sidebar/workspace `/admin`, thẻ KPI, bảng nghiệp vụ, biểu mẫu quản trị, trung tâm AI.
  - `globals.css`: Điểm nhập gọn gàng kết nối các module trên và đảm bảo không sót bất kỳ class nào đang dùng trong toàn bộ component.
- Cập nhật tài liệu hệ thống thiết kế tại `design-system/hotelai-liquid-glass/MASTER.md` làm nguồn tham chiếu chuẩn duy nhất mới.
