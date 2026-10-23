ALTER TABLE application_documents ADD COLUMN content_sha256 VARCHAR(64);
ALTER TABLE application_documents ADD CONSTRAINT ck_application_documents_sha256 CHECK (
    content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'
);
CREATE UNIQUE INDEX uq_application_documents_content
    ON application_documents (application_id, document_type, content_sha256)
    WHERE content_sha256 IS NOT NULL;
