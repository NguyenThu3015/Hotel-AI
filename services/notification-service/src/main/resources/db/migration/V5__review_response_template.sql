INSERT INTO notification_templates
    (id, code, channel, locale, subject, body, active, created_at, updated_at, version)
VALUES
    ('01000000-0000-0000-0000-000000000012', 'REVIEW_RESPONSE', 'EMAIL', 'vi',
     'Khách sạn đã phản hồi đánh giá của bạn',
     'Khách sạn đã phản hồi đánh giá cho đặt phòng {{reservationReference}}: '
         || '{{responseContent}}',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
