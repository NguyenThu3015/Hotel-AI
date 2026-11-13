CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    hotel_id VARCHAR(50),
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_accounts_email UNIQUE (email),
    CONSTRAINT ck_account_type CHECK (type IN ('CUSTOMER', 'STAFF')),
    CONSTRAINT ck_account_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE account_roles (
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_id)
);

CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_version BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(100),
    user_agent VARCHAR(500),
    ip_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_auth_sessions_account ON auth_sessions(account_id);

CREATE TABLE otp_challenges (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose VARCHAR(30) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_otp_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
CREATE INDEX ix_otp_lookup ON otp_challenges(account_id, purpose, created_at DESC);

CREATE TABLE rate_limits (
    id UUID PRIMARY KEY,
    subject_hash VARCHAR(64) NOT NULL,
    action VARCHAR(50) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_rate_limit UNIQUE (subject_hash, action)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    operation VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency UNIQUE (operation, idempotency_key)
);

-- Immutable business-event rows. Delivery state is deliberately stored separately.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    correlation_id UUID NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_outbox_occurred ON outbox_events(occurred_at);

CREATE TABLE outbox_deliveries (
    event_id UUID PRIMARY KEY REFERENCES outbox_events(id) ON DELETE CASCADE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO permissions (id, code, description) VALUES
('10000000-0000-0000-0000-000000000001', 'profile:read', 'Read own profile'),
('10000000-0000-0000-0000-000000000002', 'profile:write', 'Update own profile'),
('10000000-0000-0000-0000-000000000003', 'staff:read', 'Read staff accounts'),
('10000000-0000-0000-0000-000000000004', 'staff:write', 'Create and update staff accounts'),
('10000000-0000-0000-0000-000000000005', 'staff:lock', 'Lock and unlock staff accounts'),
('10000000-0000-0000-0000-000000000006', 'staff:reset-password', 'Request a staff password reset'),
('10000000-0000-0000-0000-000000000007', 'roles:read', 'Read roles and permissions'),
('10000000-0000-0000-0000-000000000008', 'roles:write', 'Create and update roles and assignments'),
('10000000-0000-0000-0000-000000000009', 'booking:read', 'Read reservations'),
('10000000-0000-0000-0000-000000000010', 'booking:write', 'Create and update reservations'),
('10000000-0000-0000-0000-000000000011', 'stay:write', 'Operate check-in and check-out'),
('10000000-0000-0000-0000-000000000012', 'room:write', 'Manage rooms'),
('10000000-0000-0000-0000-000000000013', 'pricing:write', 'Manage prices'),
('10000000-0000-0000-0000-000000000014', 'report:read', 'Read reports'),
('10000000-0000-0000-0000-000000000015', 'ai:staff', 'Use staff AI assistant');

INSERT INTO roles (id, code, name, description, system_role) VALUES
('20000000-0000-0000-0000-000000000001', 'CUSTOMER', 'Customer', 'Hotel customer', TRUE),
('20000000-0000-0000-0000-000000000002', 'RECEPTIONIST', 'Receptionist', 'Front desk employee', TRUE),
('20000000-0000-0000-0000-000000000003', 'HOUSEKEEPER', 'Housekeeper', 'Room operations employee', TRUE),
('20000000-0000-0000-0000-000000000004', 'MANAGER', 'Manager', 'Hotel manager', TRUE),
('20000000-0000-0000-0000-000000000005', 'ADMIN', 'Administrator', 'Identity administrator', TRUE);

INSERT INTO role_permissions(role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000001', id FROM permissions WHERE code IN ('profile:read','profile:write','booking:read','booking:write');
INSERT INTO role_permissions(role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000002', id FROM permissions WHERE code IN ('profile:read','profile:write','booking:read','booking:write','stay:write','ai:staff');
INSERT INTO role_permissions(role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000003', id FROM permissions WHERE code IN ('profile:read','profile:write','room:write','ai:staff');
INSERT INTO role_permissions(role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000004', id FROM permissions WHERE code IN ('profile:read','profile:write','staff:read','staff:write','staff:lock','staff:reset-password','roles:read','booking:read','booking:write','stay:write','room:write','pricing:write','report:read','ai:staff');
INSERT INTO role_permissions(role_id, permission_id)
SELECT '20000000-0000-0000-0000-000000000005', id FROM permissions;
