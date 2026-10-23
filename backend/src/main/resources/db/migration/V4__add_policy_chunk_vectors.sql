-- Prerequisite: confirm pgvector is available and enabled on the intended Neon branch.
-- This migration is deliberately not applied automatically by the application.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE policy_documents DROP CONSTRAINT ck_policy_documents_status;
ALTER TABLE policy_documents ADD CONSTRAINT ck_policy_documents_status CHECK (status IN (
    'REGISTERED', 'ACTIVE', 'ARCHIVED', 'UPLOADED', 'PROCESSING',
    'PROCESSED', 'INDEXED', 'NEEDS_OCR', 'FAILED'
));

ALTER TABLE policy_chunks ADD COLUMN embedding vector(1536);
CREATE INDEX ix_policy_chunks_embedding_hnsw
    ON policy_chunks USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
