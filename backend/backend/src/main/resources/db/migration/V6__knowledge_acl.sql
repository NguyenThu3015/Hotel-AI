ALTER TABLE documents
    ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN allowed_roles JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE documents
    ADD CONSTRAINT ck_documents_audience CHECK (audience IN ('PUBLIC', 'STAFF'));
