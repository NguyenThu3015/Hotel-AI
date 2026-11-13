CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    source VARCHAR(500),
    document_type VARCHAR(40) NOT NULL DEFAULT 'POLICY',
    content TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_embedding
    ON document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_name VARCHAR(100),
    tool_payload JSONB,
    grounded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE reviews
    ADD COLUMN sentiment VARCHAR(20),
    ADD COLUMN sentiment_score NUMERIC(5,4),
    ADD COLUMN topics JSONB;

INSERT INTO documents(title, source, document_type, content)
VALUES
('Chính sách nhận và trả phòng', 'Nội bộ', 'POLICY',
 'Giờ nhận phòng tiêu chuẩn là 14:00 và giờ trả phòng là 12:00. Khách cần xuất trình giấy tờ tùy thân hợp lệ khi nhận phòng.'),
('Chính sách hủy phòng', 'Nội bộ', 'POLICY',
 'Booking đang chờ thanh toán được hủy miễn phí. Booking đã xác nhận hủy sát ngày nhận phòng có thể chịu phí theo chính sách hiển thị trên booking.'),
('Tiện nghi và dịch vụ', 'Nội bộ', 'FAQ',
 'Khách sạn cung cấp WiFi, điều hòa, bữa sáng, hồ bơi, giặt ủi, minibar, đưa đón và giường phụ tùy loại phòng.');
