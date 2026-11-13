\set ON_ERROR_STOP on

\connect ai_conversation_db

BEGIN;

-- Vietnamese RAG corpus for guest chat and the role-aware staff assistant.
-- Stable source keys make this file safe to run repeatedly.
INSERT INTO documents(title, source, document_type, content, audience, allowed_roles, active)
VALUES
    ('Quy định nhận phòng và trả phòng', 'seed:ai-demo:guest-check-in-out', 'POLICY',
     'Giờ nhận phòng tiêu chuẩn là 14:00 và giờ trả phòng là 12:00. Khách đến sớm có thể gửi hành lý miễn phí tại lễ tân; việc nhận phòng sớm phụ thuộc tình trạng phòng và có thể phát sinh phí. Trả phòng muộn cần được lễ tân xác nhận trước. Khi nhận phòng, khách xuất trình giấy tờ tùy thân hợp lệ của người đứng tên đặt phòng. Yêu cầu đặc biệt chỉ được xác nhận sau khi khách sạn kiểm tra khả năng đáp ứng.',
     'PUBLIC', '[]'::jsonb, true),
    ('Chính sách hủy, đổi ngày và vắng mặt', 'seed:ai-demo:guest-cancellation-change-noshow', 'POLICY',
     'Đặt phòng đang chờ thanh toán có thể hủy miễn phí trước khi hết thời gian giữ chỗ. Với đặt phòng đã xác nhận, phí hủy được tính theo chính sách và giá tại thời điểm đặt. Đổi ngày phải còn phòng, hệ thống sẽ tính lại giá theo ngày mới và chỉ hoàn tất sau khi khách xác nhận phần chênh lệch. Khách không đến trước thời hạn quy định và không thông báo có thể bị ghi nhận vắng mặt; tiền cọc được xử lý theo điều kiện giá của booking.',
     'PUBLIC', '[]'::jsonb, true),
    ('Thanh toán chuyển khoản và xác nhận giao dịch', 'seed:ai-demo:guest-bank-transfer', 'PAYMENT_GUIDE',
     'Khi thanh toán chuyển khoản, khách phải giữ nguyên số tiền và nội dung chuyển khoản do HotelAI hiển thị. Casso gửi webhook đối soát sau khi ngân hàng ghi nhận giao dịch; thời gian xác nhận thường từ vài giây đến vài phút. Không tạo giao dịch mới khi giao dịch cũ đang chờ đối soát. Nếu đã chuyển tiền nhưng chưa cập nhật sau 10 phút, khách cung cấp mã booking và ảnh biên lai cho lễ tân; không gửi số thẻ, mật khẩu hay mã OTP qua chatbot.',
     'PUBLIC', '[]'::jsonb, true),
    ('Giá phòng, thuế, phí và khuyến mãi', 'seed:ai-demo:guest-pricing', 'PRICING_GUIDE',
     'Giá phòng thay đổi theo ngày lưu trú, hạng phòng, số phòng, sức chứa và khuyến mãi còn hiệu lực. Tổng tiền hiển thị tại bước xác nhận là giá của toàn kỳ nghỉ. Thuế, phí dịch vụ và giảm giá được tách thành từng dòng để khách kiểm tra. Mã ưu đãi chỉ áp dụng khi thỏa thời gian, hạng phòng, giá trị tối thiểu và số lượt sử dụng. Chatbot chỉ báo giá từ dữ liệu tồn phòng thời gian thực, không tự cam kết giữ mức giá cũ.',
     'PUBLIC', '[]'::jsonb, true),
    ('Hạng phòng và tiêu chí lựa chọn', 'seed:ai-demo:guest-room-types', 'CATALOG_GUIDE',
     'Khách nên chọn hạng phòng theo số người lớn, trẻ em, loại giường, ngân sách và tiện nghi. Standard và Superior phù hợp kỳ nghỉ gọn nhẹ; Deluxe và Deluxe Twin phù hợp cặp đôi hoặc khách công tác; Family Suite dành cho gia đình; Executive và HotelAI Suite có không gian làm việc hoặc phòng khách; Presidential Suite là hạng cao cấp nhất. Số phòng còn lại luôn phụ thuộc ngày nhận, ngày trả và số lượng phòng tìm kiếm.',
     'PUBLIC', '[]'::jsonb, true),
    ('Trẻ em, giường phụ và sức chứa', 'seed:ai-demo:guest-children-capacity', 'POLICY',
     'Sức chứa tối đa được kiểm tra theo từng hạng phòng. Trẻ em phải được khai báo đúng số lượng khi tìm phòng để hệ thống loại bỏ lựa chọn không phù hợp. Nôi em bé và giường phụ là yêu cầu có giới hạn, cần lễ tân xác nhận và có thể tính phí. Nếu số khách thực tế vượt sức chứa đã đặt, khách sạn có thể yêu cầu nâng hạng, đặt thêm phòng hoặc từ chối nhận phòng để bảo đảm an toàn.',
     'PUBLIC', '[]'::jsonb, true),
    ('Dịch vụ trong kỳ lưu trú', 'seed:ai-demo:guest-stay-services', 'FAQ',
     'Khách đang lưu trú có thể yêu cầu bữa sáng, giặt ủi, minibar, đưa đón sân bay và các dịch vụ bổ sung đang được khách sạn công khai. Giá được tính theo đơn vị và số lượng, sau đó cộng vào hồ sơ lưu trú và hóa đơn. Mọi yêu cầu phụ thuộc giờ phục vụ và khả năng đáp ứng. Khách có thể theo dõi dịch vụ đã dùng trong mục Kỳ nghỉ của tôi hoặc liên hệ lễ tân để điều chỉnh trước khi dịch vụ được thực hiện.',
     'PUBLIC', '[]'::jsonb, true),
    ('Bảo mật tài khoản và dữ liệu cá nhân', 'seed:ai-demo:guest-privacy-security', 'SECURITY_GUIDE',
     'HotelAI không yêu cầu khách cung cấp mật khẩu, mã OTP, số thẻ đầy đủ hoặc ảnh giấy tờ tùy thân trong chatbot. Khách chỉ nhập dữ liệu nhạy cảm tại biểu mẫu bảo mật tương ứng. Khi nghi ngờ tài khoản bị truy cập trái phép, khách đổi mật khẩu, đăng xuất các phiên cũ và liên hệ quản trị viên. Thông tin đặt phòng chỉ được hiển thị cho tài khoản sở hữu hoặc nhân viên có quyền nghiệp vụ phù hợp.',
     'PUBLIC', '[]'::jsonb, true),
    ('Đánh giá, khiếu nại và đồ thất lạc', 'seed:ai-demo:guest-feedback-lost-found', 'FAQ',
     'Khách đã hoàn tất lưu trú có thể gửi đánh giá về phòng và dịch vụ. Nội dung có dấu hiệu khẩn cấp, an toàn hoặc khiếu nại nghiêm trọng được ưu tiên chuyển nhân viên xử lý. Với đồ thất lạc, khách cung cấp mã booking, mô tả đồ vật, vị trí và thời gian cuối cùng nhìn thấy; không gửi thông tin tài chính trong nội dung. Việc tìm thấy, lưu giữ và hoàn trả tài sản phải được lễ tân xác minh.',
     'PUBLIC', '[]'::jsonb, true),
    ('Quy trình nhận phòng tại quầy', 'seed:ai-demo:staff-check-in', 'INTERNAL_PROCEDURE',
     'Lễ tân tra cứu booking, đối chiếu trạng thái xác nhận và thanh toán, xác minh giấy tờ của người đứng tên, kiểm tra số khách và yêu cầu đặc biệt. Chỉ phân phòng đang AVAILABLE và sạch. Sau khi ghi nhận danh sách khách lưu trú, nhân viên xác nhận check-in một lần; hệ thống chuyển phòng sang OCCUPIED và phát sinh hồ sơ stay. Nếu dữ liệu vừa thay đổi, tải lại bản ghi mới trước khi thao tác, không lặp yêu cầu với version cũ.',
     'STAFF', '["RECEPTIONIST","MANAGER","ADMIN"]'::jsonb, true),
    ('Quy trình đổi phòng và gia hạn lưu trú', 'seed:ai-demo:staff-room-change-extension', 'INTERNAL_PROCEDURE',
     'Đổi phòng cần kiểm tra phòng đích còn trống, lý do đổi, chênh lệch giá và trạng thái vệ sinh. Nhân viên thông báo chi phí cho khách trước khi xác nhận; phòng cũ chuyển chờ dọn và phòng mới chuyển đang có khách. Gia hạn phải kiểm tra tồn phòng cho toàn khoảng ngày bổ sung, tính lại giá và ghi nhận khoản phải thu. Không sửa trực tiếp ngày hoặc phòng trong cơ sở dữ liệu; luôn dùng thao tác nghiệp vụ của Stay Service.',
     'STAFF', '["RECEPTIONIST","MANAGER","ADMIN"]'::jsonb, true),
    ('Quy trình trả phòng và lập hóa đơn', 'seed:ai-demo:staff-checkout-billing', 'INTERNAL_PROCEDURE',
     'Trước check-out, lễ tân rà soát tiền phòng, dịch vụ đã sử dụng, phụ thu, khuyến mãi, khoản đã trả và công nợ còn lại. Các dịch vụ chưa chốt phải được xác minh với bộ phận liên quan. Chỉ hoàn tất trả phòng khi số tiền phải thu được xử lý theo chính sách. Billing Service lập hóa đơn từ snapshot đã chốt; nhân viên kiểm tra tên người mua, mã số thuế và email trước khi phát hành. Sau check-out, phòng chuyển WAITING_CLEANING.',
     'STAFF', '["RECEPTIONIST","MANAGER","ADMIN"]'::jsonb, true),
    ('Xử lý đối soát Casso và thanh toán trùng', 'seed:ai-demo:staff-casso-reconciliation', 'INTERNAL_PROCEDURE',
     'Mỗi giao dịch Casso được xác minh chữ ký webhook, tài khoản ngân hàng, số tiền, nội dung chuyển khoản và mã giao dịch duy nhất. Khi giao dịch khớp, Payment Service ghi nhận thanh toán theo cơ chế idempotent và cập nhật booking. Nếu webhook đến lặp lại, hệ thống trả kết quả đã xử lý thay vì cộng tiền lần nữa. Giao dịch thiếu hoặc sai nội dung được đưa vào danh sách cần đối soát; nhân viên không tự gán khi chưa kiểm tra chứng từ và lịch sử thanh toán.',
     'STAFF', '["RECEPTIONIST","MANAGER","ADMIN"]'::jsonb, true),
    ('Quy trình hoàn tiền và xử lý chênh lệch', 'seed:ai-demo:staff-refund', 'INTERNAL_PROCEDURE',
     'Hoàn tiền chỉ được tạo từ khoản thanh toán hợp lệ và không vượt số tiền có thể hoàn. Nhân viên chọn lý do, số tiền, phương thức và gửi yêu cầu; quản lý phê duyệt nếu vượt ngưỡng nghiệp vụ. Với chuyển khoản ngân hàng, trạng thái có thể ở chờ xử lý đến khi kế toán xác nhận. Không đánh dấu hoàn tất thủ công nếu chưa có bằng chứng giao dịch. Mọi lần thử, phê duyệt và thay đổi trạng thái phải có nhật ký kiểm toán.',
     'STAFF', '["MANAGER","ADMIN"]'::jsonb, true),
    ('Vòng đời vệ sinh và bảo trì phòng', 'seed:ai-demo:staff-housekeeping-maintenance', 'INTERNAL_PROCEDURE',
     'Phòng vừa check-out chuyển WAITING_CLEANING. Nhân viên buồng phòng nhận việc, cập nhật tiến độ và chỉ chuyển AVAILABLE sau khi hoàn tất kiểm tra vệ sinh. Phòng phát hiện hỏng hóc chuyển MAINTENANCE, ghi rõ mức độ, mô tả và thời gian dự kiến hoàn thành; phòng này không được bán. Quản lý theo dõi phòng quá hạn dọn hoặc bảo trì và điều phối ưu tiên theo lịch khách đến.',
     'STAFF', '["HOUSEKEEPER","MANAGER","ADMIN"]'::jsonb, true),
    ('Xử lý khách vắng mặt và booking rủi ro cao', 'seed:ai-demo:staff-no-show-risk', 'INTERNAL_PROCEDURE',
     'Mô hình AI chỉ đưa ra xác suất và yếu tố rủi ro, không tự hủy booking. Nhân viên ưu tiên liên hệ các booking rủi ro cao gần ngày nhận phòng, xác minh ý định đến và hướng dẫn thanh toán cọc nếu cần. Chỉ ghi nhận NO_SHOW sau thời hạn nhận phòng của khách sạn và khi đã thực hiện bước liên hệ theo quy trình. Không đối xử bất lợi với khách dựa trên dữ liệu nhạy cảm; quyết định cuối cùng dựa trên trạng thái booking và chính sách công bố.',
     'STAFF', '["RECEPTIONIST","MANAGER","ADMIN"]'::jsonb, true),
    ('Phê duyệt giá do AI đề xuất', 'seed:ai-demo:staff-revenue-approval', 'INTERNAL_PROCEDURE',
     'AI Revenue Optimization đề xuất hệ số giá dựa trên dự báo nhu cầu, công suất, tồn phòng và dữ liệu lịch sử. Mỗi hạng phòng phải có giá sàn và giá trần. Đề xuất không tự công bố: quản lý xem căn cứ, mức tin cậy và tác động dự kiến, sau đó phê duyệt hoặc từ chối. Giá được phê duyệt tạo phiên bản mới có người duyệt, thời điểm hiệu lực và lịch sử bất biến; không ghi đè phiên bản đã phát hành.',
     'STAFF', '["MANAGER","ADMIN"]'::jsonb, true),
    ('Xử lý đánh giá bằng AI và phản hồi khách', 'seed:ai-demo:staff-review-analytics', 'INTERNAL_PROCEDURE',
     'AI phân loại cảm xúc, chủ đề và mức độ khẩn cấp của đánh giá để hỗ trợ ưu tiên. Nội dung liên quan an toàn, mất tài sản, phân biệt đối xử hoặc sự cố nghiêm trọng phải được chuyển quản lý ngay. Bản nháp phản hồi của AI cần nhân viên kiểm tra dữ kiện và giọng điệu trước khi công khai. Không tiết lộ thông tin đặt phòng, tên nhân viên hoặc dữ liệu cá nhân trong phản hồi công khai.',
     'STAFF', '["MANAGER","ADMIN"]'::jsonb, true),
    ('Nguyên tắc sử dụng trợ lý AI nội bộ', 'seed:ai-demo:staff-ai-governance', 'INTERNAL_PROCEDURE',
     'Trợ lý AI nội bộ chỉ truy xuất tài liệu và dữ liệu phù hợp với vai trò đăng nhập. AI có thể tóm tắt, tra cứu và gợi ý bước xử lý nhưng không tự đặt phòng, đổi phòng, hoàn tiền, phát hành hóa đơn hoặc công bố giá. Nhân viên phải kiểm tra màn hình nghiệp vụ trước khi hành động. Không nhập mật khẩu, OTP, số thẻ đầy đủ hoặc bản chụp giấy tờ vào hội thoại. Khi câu trả lời thiếu nguồn hoặc dữ liệu, nhân viên chuyển sang quy trình thủ công.',
     'STAFF', '["RECEPTIONIST","HOUSEKEEPER","MANAGER","ADMIN"]'::jsonb, true),
    ('Quy trình xử lý sự cố và khiếu nại khẩn cấp', 'seed:ai-demo:staff-incident-escalation', 'INTERNAL_PROCEDURE',
     'Sự cố liên quan sức khỏe, an toàn, cháy nổ, an ninh hoặc mất tài sản được xếp mức khẩn cấp. Nhân viên bảo đảm an toàn trước, gọi bộ phận chịu trách nhiệm và quản lý ca, sau đó ghi nhận thời gian, vị trí, người xử lý và diễn biến. Không tranh luận trách nhiệm với khách tại hiện trường. Khiếu nại thông thường phải được phản hồi trong ca làm việc; vấn đề chưa giải quyết được bàn giao rõ ràng cho ca sau.',
     'STAFF', '["RECEPTIONIST","HOUSEKEEPER","MANAGER","ADMIN"]'::jsonb, true)
ON CONFLICT (source) WHERE source IS NOT NULL DO UPDATE SET
    title = EXCLUDED.title,
    document_type = EXCLUDED.document_type,
    content = EXCLUDED.content,
    audience = EXCLUDED.audience,
    allowed_roles = EXCLUDED.allowed_roles,
    active = true,
    updated_at = CURRENT_TIMESTAMP;

-- The conversation service will rebuild vectors for these documents at its
-- next startup. Existing user-created knowledge remains untouched.
DELETE FROM document_chunks
WHERE document_id IN (
    SELECT id FROM documents WHERE source LIKE 'seed:ai-demo:%'
);

COMMIT;
