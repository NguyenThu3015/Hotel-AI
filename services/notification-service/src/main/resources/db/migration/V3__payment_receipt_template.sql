INSERT INTO notification_templates
    (id, code, channel, locale, subject, body, active, created_at, updated_at, version)
VALUES
    ('01000000-0000-0000-0000-000000000010', 'PAYMENT_RECEIPT', 'EMAIL', 'vi',
     'Biên nhận thanh toán {{paymentId}}',
     'Thanh toán {{paymentId}} cho đặt phòng {{bookingCode}} đã thành công. '
         || 'Số tiền: {{amount}} {{currency}}. Mã giao dịch: {{providerReference}}. '
         || 'Xem hoặc tải biên nhận tại {{receiptPath}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
