CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL,
    subject VARCHAR(300),
    body TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_code_channel_locale UNIQUE (code, channel, locale),
    CONSTRAINT ck_template_channel CHECK (channel IN ('EMAIL', 'SMS', 'ZALO'))
);

CREATE TABLE notification_jobs (
    id UUID PRIMARY KEY,
    source_event_id VARCHAR(120) NOT NULL UNIQUE,
    correlation_id VARCHAR(120),
    recipient_identity_id UUID,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(500) NOT NULL,
    subject VARCHAR(300),
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    external_reference VARCHAR(300),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL', 'SMS', 'ZALO')),
    CONSTRAINT ck_notification_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'SENT', 'FAILED', 'DEAD_LETTERED', 'CANCELLED')),
    CONSTRAINT ck_notification_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 10)
);
CREATE INDEX idx_notification_due ON notification_jobs(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY_SCHEDULED');
CREATE INDEX idx_notification_recipient ON notification_jobs(recipient_identity_id, created_at DESC);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES notification_jobs(id),
    attempt_number INTEGER NOT NULL,
    successful BOOLEAN NOT NULL,
    provider_reference VARCHAR(300),
    error TEXT,
    attempted_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_delivery_attempt UNIQUE (job_id, attempt_number)
);

CREATE TABLE inbox_events (
    event_id VARCHAR(120) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL
);

INSERT INTO notification_templates
    (id, code, channel, locale, subject, body, active, created_at, updated_at, version)
VALUES
    ('01000000-0000-0000-0000-000000000001', 'BOOKING_CONFIRMED', 'EMAIL', 'vi',
     'Xác nhận đặt phòng {{bookingCode}}',
     'Xin chào {{customerName}}, đặt phòng {{bookingCode}} của bạn đã được xác nhận.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000002', 'BOOKING_REMINDER', 'SMS', 'vi', NULL,
     'Nhắc lịch: đặt phòng {{bookingCode}} sẽ nhận phòng lúc {{checkInTime}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000003', 'BOOKING_REMINDER', 'ZALO', 'vi', NULL,
     'Nhắc lịch đặt phòng {{bookingCode}}: thời gian nhận phòng {{checkInTime}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000004', 'OTP_ACCOUNT_ACTIVATION', 'EMAIL', 'vi',
     'Mã xác thực tài khoản', 'Mã OTP của bạn là {{otp}}. Mã hết hạn lúc {{expiresAt}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000005', 'OTP_PASSWORD_RESET', 'EMAIL', 'vi',
     'Đặt lại mật khẩu', 'Mã OTP đặt lại mật khẩu là {{otp}}. Mã hết hạn lúc {{expiresAt}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000006', 'OTP_LOGIN_MFA', 'EMAIL', 'vi',
     'Xác minh đăng nhập', 'Mã xác minh đăng nhập là {{otp}}. Mã hết hạn lúc {{expiresAt}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000007', 'OTP_ACCOUNT_ACTIVATION', 'SMS', 'vi', NULL,
     'OTP kích hoạt tài khoản: {{otp}}. Hết hạn: {{expiresAt}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000008', 'OTP_PASSWORD_RESET', 'SMS', 'vi', NULL,
     'OTP đặt lại mật khẩu: {{otp}}. Hết hạn: {{expiresAt}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('01000000-0000-0000-0000-000000000009', 'OTP_LOGIN_MFA', 'SMS', 'vi', NULL,
     'OTP xác minh đăng nhập: {{otp}}. Hết hạn: {{expiresAt}}.',
     TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
