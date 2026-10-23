ALTER TABLE policy_documents
    ADD COLUMN file_size_bytes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN content_sha256 VARCHAR(64),
    ADD COLUMN processing_error VARCHAR(500),
    ADD COLUMN extraction_completed_at TIMESTAMPTZ;

ALTER TABLE policy_documents
    DROP CONSTRAINT ck_policy_documents_status;

ALTER TABLE policy_documents
    ADD CONSTRAINT ck_policy_documents_status CHECK (status IN (
        'REGISTERED', 'ACTIVE', 'ARCHIVED', 'UPLOADED', 'PROCESSING',
        'PROCESSED', 'NEEDS_OCR', 'FAILED'
    ));

ALTER TABLE policy_documents
    ADD CONSTRAINT ck_policy_documents_file_size CHECK (file_size_bytes >= 0),
    ADD CONSTRAINT ck_policy_documents_sha256 CHECK (
        content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'
    );

CREATE UNIQUE INDEX uq_policy_documents_storage_key
    ON policy_documents (storage_key)
    WHERE storage_key IS NOT NULL;

CREATE TABLE policy_document_pages (
    id UUID PRIMARY KEY,
    policy_document_id UUID NOT NULL REFERENCES policy_documents (id) ON DELETE RESTRICT,
    page_number INTEGER NOT NULL,
    extracted_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_policy_document_pages_page_number CHECK (page_number >= 1),
    CONSTRAINT uq_policy_document_pages_document_page UNIQUE (policy_document_id, page_number)
);

CREATE INDEX ix_policy_document_pages_document_page
    ON policy_document_pages (policy_document_id, page_number);
