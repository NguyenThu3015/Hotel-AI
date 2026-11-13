UPDATE notification_templates
SET subject = 'Xác nhận đặt phòng {{bookingCode}}',
    body = 'Xin chào {{customerName}}, đặt phòng {{bookingCode}} của bạn đã được xác nhận.'
        || E'\nNhận phòng: {{checkIn}} - Trả phòng: {{checkOut}}.'
        || E'\nSố phòng: {{roomCount}}. Tổng tiền: {{totalAmount}} {{currency}}.',
    updated_at = CURRENT_TIMESTAMP,
    version = version + 1
WHERE code = 'BOOKING_CONFIRMED' AND channel = 'EMAIL' AND locale = 'vi';

INSERT INTO notification_templates
    (id, code, channel, locale, subject, body, active, created_at, updated_at, version)
VALUES
    ('01000000-0000-0000-0000-000000000013', 'BOOKING_RECEIVED', 'EMAIL', 'vi',
     'Đã tiếp nhận đặt phòng {{bookingCode}}',
     'Xin chào {{customerName}}, khách sạn đã tiếp nhận đặt phòng {{bookingCode}}.'
        || E'\nTrạng thái hiện tại: {{status}}.'
        || E'\nNhận phòng: {{checkIn}} - Trả phòng: {{checkOut}}.'
        || E'\nTổng tiền: {{totalAmount}} {{currency}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000014', 'BOOKING_CHANGED', 'EMAIL', 'vi',
     'Đặt phòng {{bookingCode}} đã được cập nhật',
     'Xin chào {{customerName}}, thông tin đặt phòng {{bookingCode}} đã được cập nhật.'
        || E'\nNhận phòng: {{checkIn}} - Trả phòng: {{checkOut}}.'
        || E'\nSố phòng: {{roomCount}}. Tổng tiền mới: {{totalAmount}} {{currency}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000015', 'BOOKING_CANCELLED', 'EMAIL', 'vi',
     'Đặt phòng {{bookingCode}} đã được hủy',
     'Xin chào {{customerName}}, đặt phòng {{bookingCode}} đã được hủy.'
        || E'\nLý do: {{reason}}. Phí hủy: {{cancellationFee}} {{currency}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000016', 'BOOKING_REJECTED', 'EMAIL', 'vi',
     'Đặt phòng {{bookingCode}} chưa được chấp nhận',
     'Xin chào {{customerName}}, đặt phòng {{bookingCode}} chưa được khách sạn chấp nhận.'
        || E'\nLý do: {{reason}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000017', 'BOOKING_EXPIRED', 'EMAIL', 'vi',
     'Giữ phòng {{bookingCode}} đã hết hạn',
     'Xin chào {{customerName}}, thời gian giữ phòng cho đặt phòng {{bookingCode}} đã hết hạn.'
        || E'\nBạn có thể tạo một đặt phòng mới nếu vẫn muốn lưu trú.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000018', 'PAYMENT_FAILED', 'EMAIL', 'vi',
     'Thanh toán cho đặt phòng {{bookingCode}} chưa thành công',
     'Thanh toán cho đặt phòng {{bookingCode}} chưa thành công hoặc đã hết thời gian xử lý.'
        || E'\nVui lòng kiểm tra lại giao dịch trước khi thử thanh toán lại.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000019', 'PAYMENT_CANCELLED', 'EMAIL', 'vi',
     'Giao dịch {{paymentId}} đã được hủy',
     'Giao dịch {{paymentId}} cho đặt phòng {{bookingCode}} đã được hủy.'
        || E'\nSố tiền: {{amount}} {{currency}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000020', 'CHECK_IN_COMPLETED', 'EMAIL', 'vi',
     'Đã nhận phòng - {{bookingCode}}',
     'Xin chào {{customerName}}, thủ tục nhận phòng cho đặt phòng {{bookingCode}} đã hoàn tất.'
        || E'\nPhòng: {{roomNumbers}}. Thời gian nhận phòng: {{actualCheckIn}}.'
        || E'\nChúc bạn có kỳ nghỉ thật thoải mái!',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000021', 'ROOM_CHANGED', 'EMAIL', 'vi',
     'Thông tin đổi phòng - {{bookingCode}}',
     'Xin chào {{customerName}}, yêu cầu đổi phòng của đặt phòng {{bookingCode}} đã hoàn tất.'
        || E'\nPhòng hiện tại: {{roomNumbers}}. Số dư cần thanh toán: {{balance}} {{currency}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000022', 'STAY_EXTENDED', 'EMAIL', 'vi',
     'Đã gia hạn lưu trú - {{bookingCode}}',
     'Xin chào {{customerName}}, thời gian lưu trú của đặt phòng {{bookingCode}} đã được gia hạn.'
        || E'\nNgày trả phòng mới: {{scheduledCheckOut}}.'
        || E'\nSố dư cần thanh toán: {{balance}} {{currency}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000023', 'CHECKOUT_PAYMENT_PENDING', 'EMAIL', 'vi',
     'Cần hoàn tất thanh toán khi trả phòng - {{bookingCode}}',
     'Đặt phòng {{bookingCode}} còn số dư {{balance}} {{currency}} trước khi hoàn tất trả phòng.'
        || E'\nVui lòng liên hệ lễ tân để được hỗ trợ.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000024', 'CHECK_OUT_COMPLETED', 'EMAIL', 'vi',
     'Đã hoàn tất trả phòng - {{bookingCode}}',
     'Xin chào {{customerName}}, thủ tục trả phòng cho đặt phòng {{bookingCode}} đã hoàn tất.'
        || E'\nThời gian trả phòng: {{actualCheckOut}}.'
        || E'\nTổng chi phí: {{chargesTotal}} {{currency}}. Đã thanh toán: {{paidTotal}} {{currency}}.'
        || E'\nCảm ơn bạn đã lựa chọn khách sạn!',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000025', 'PAYMENT_REFUNDED', 'EMAIL', 'vi',
     'Đã hoàn tiền cho đặt phòng {{bookingCode}}',
     'Yêu cầu hoàn tiền {{refundId}} cho đặt phòng {{bookingCode}} đã hoàn tất.'
        || E'\nSố tiền hoàn: {{amount}} {{currency}}.'
        || E'\nMã giao dịch hoàn tiền: {{providerReference}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000026', 'INVOICE_CORRECTION', 'EMAIL', 'vi',
     'Điều chỉnh hóa đơn {{invoiceNumber}}',
     'Xin chào {{buyerName}}, hóa đơn {{invoiceNumber}} đã có phiếu điều chỉnh {{correctionNumber}}.'
        || E'\nLoại điều chỉnh: {{correctionType}}. Chênh lệch: {{amountDelta}} {{currency}}.'
        || E'\nLý do: {{reason}}. Xem hóa đơn tại {{downloadPath}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000027', 'BOOKING_REMINDER', 'EMAIL', 'vi',
     'Nhắc lịch nhận phòng {{bookingCode}}',
     'Xin chào, đặt phòng {{bookingCode}} sắp đến thời gian nhận phòng {{checkInTime}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
