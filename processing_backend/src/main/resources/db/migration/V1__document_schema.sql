-- Document Processing Service schema (owns document_db)
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id                    UUID PRIMARY KEY,
    owner_user_id         UUID NOT NULL,
    original_filename     VARCHAR(255) NOT NULL,
    content_type          VARCHAR(100) NOT NULL,
    file_size             BIGINT NOT NULL,
    checksum_sha256       VARCHAR(64) NOT NULL,
    s3_bucket             VARCHAR(255),
    s3_key                VARCHAR(1024),
    status                VARCHAR(40) NOT NULL,
    pii_detected          BOOLEAN NOT NULL DEFAULT FALSE,
    pii_decision          VARCHAR(20),
    masking_applied       BOOLEAN NOT NULL DEFAULT FALSE,
    page_count            INTEGER,
    chunk_count           INTEGER,
    embedding_provider    VARCHAR(50),
    embedding_model       VARCHAR(150),
    embedding_dimensions  INTEGER,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_owner_user_id ON documents (owner_user_id);
CREATE INDEX idx_documents_owner_status ON documents (owner_user_id, status);
CREATE INDEX idx_documents_checksum ON documents (owner_user_id, checksum_sha256);

CREATE TABLE pii_findings (
    id                 UUID PRIMARY KEY,
    document_id        UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    entity_type        VARCHAR(50) NOT NULL,
    page_number        INTEGER,
    confidence         DOUBLE PRECISION,
    occurrence_count   INTEGER NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pii_findings_document_id ON pii_findings (document_id);

CREATE TABLE document_chunks (
    id                   UUID PRIMARY KEY,
    document_id          UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    owner_user_id        UUID NOT NULL,
    chunk_index          INTEGER NOT NULL,
    content              TEXT NOT NULL,
    page_start           INTEGER,
    page_end             INTEGER,
    token_count          INTEGER,
    embedding            VECTOR(1024),
    embedding_provider   VARCHAR(50),
    embedding_model      VARCHAR(150),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_document_chunks_doc_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_owner ON document_chunks (owner_user_id);
CREATE INDEX idx_document_chunks_document ON document_chunks (document_id);

CREATE TABLE processing_jobs (
    id               UUID PRIMARY KEY,
    document_id      UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    owner_user_id    UUID NOT NULL,
    status           VARCHAR(30) NOT NULL,
    attempts         INTEGER NOT NULL DEFAULT 0,
    last_error_code  VARCHAR(100),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processing_jobs_document ON processing_jobs (document_id);
CREATE INDEX idx_processing_jobs_owner_status ON processing_jobs (owner_user_id, status);
