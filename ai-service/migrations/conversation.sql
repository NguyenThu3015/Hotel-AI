CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(500),
    document_type VARCHAR(40) NOT NULL DEFAULT 'POLICY',
    content TEXT NOT NULL,
    audience VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    allowed_roles JSONB NOT NULL DEFAULT '[]'::jsonb,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ai_document_audience CHECK (audience IN ('PUBLIC','STAFF'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_documents_source
    ON documents(source) WHERE source IS NOT NULL;

CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, chunk_index)
);
CREATE INDEX IF NOT EXISTS idx_ai_document_chunks_embedding
    ON document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY,
    user_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(100),
    tool_payload JSONB,
    grounded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_messages_session_time
    ON chat_messages(session_id, created_at);

INSERT INTO documents(title,source,document_type,content,audience,allowed_roles)
VALUES
('Chính sách nhận và trả phòng','seed:check-in-out','POLICY',
 'Giờ nhận phòng tiêu chuẩn là 14:00 và giờ trả phòng là 12:00. Khách cần xuất trình giấy tờ tùy thân hợp lệ khi nhận phòng.',
 'PUBLIC','[]'::jsonb),
('Chính sách hủy phòng','seed:cancellation','POLICY',
 'Booking đang chờ thanh toán được hủy miễn phí. Booking đã xác nhận hủy sát ngày nhận phòng có thể chịu phí theo chính sách hiển thị trên booking.',
 'PUBLIC','[]'::jsonb),
('Tiện nghi và dịch vụ','seed:amenities','FAQ',
 'Khách sạn cung cấp WiFi, điều hòa, bữa sáng, hồ bơi, giặt ủi, minibar, đưa đón và giường phụ tùy loại phòng.',
 'PUBLIC','[]'::jsonb),
('Quy trình xử lý yêu cầu đặc biệt','seed:staff-special-request','INTERNAL_PROCEDURE',
 'Nhân viên phải ghi nhận yêu cầu đặc biệt trên booking, không cam kết khi chưa kiểm tra tồn và phải ghi rõ người xác nhận.',
 'STAFF','["RECEPTIONIST","MANAGER","ADMIN"]'::jsonb)
ON CONFLICT (source) WHERE source IS NOT NULL DO NOTHING;
