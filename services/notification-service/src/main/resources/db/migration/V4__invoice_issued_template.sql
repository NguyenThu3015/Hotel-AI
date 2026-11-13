INSERT INTO notification_templates
    (id, code, channel, locale, subject, body, active, created_at, updated_at, version)
VALUES
    ('01000000-0000-0000-0000-000000000011', 'INVOICE_ISSUED', 'EMAIL', 'vi',
     'Hóa đơn {{invoiceNumber}}',
     'Hóa đơn {{invoiceNumber}} cho đặt phòng {{reservationReference}} đã được phát hành. '
         || 'Tổng tiền: {{totalAmount}} {{currency}}. '
         || 'Xem hoặc tải hóa đơn tại {{downloadPath}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
