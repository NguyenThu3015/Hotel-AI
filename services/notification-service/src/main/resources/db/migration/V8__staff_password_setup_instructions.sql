UPDATE notification_templates
SET subject = 'Thiết lập mật khẩu tài khoản nhân viên HotelAI',
    body = 'Tài khoản nhân viên HotelAI của bạn đã được tạo.'
        || E'\nMở http://localhost:3000/login, chọn "Quên mật khẩu?", nhập đúng email nhận thư này rồi làm theo hướng dẫn.'
        || E'\nMã xác minh hiện tại: {{otp}}. Mã hết hạn lúc {{expiresAt}}.'
        || E'\nNếu mã hết hạn, thao tác "Quên mật khẩu?" sẽ gửi một mã mới.',
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE code = 'OTP_PASSWORD_RESET' AND channel = 'EMAIL' AND locale = 'vi';
