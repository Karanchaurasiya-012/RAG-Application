-- Phase 13: pgvector retrieval indexes.
-- Dimension VECTOR(1024) was verified against live BGE-M3 and Gemini outputDimensionality=1024
-- before this index was added (never guess the vector size).

-- Cosine-distance HNSW for nearest-neighbor search over persisted embeddings.
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE embedding IS NOT NULL;

-- Filter helpers for owner-scoped / provider-scoped retrieval (do not mix embedding spaces).
CREATE INDEX IF NOT EXISTS idx_document_chunks_owner_provider
    ON document_chunks (owner_user_id, embedding_provider)
    WHERE embedding IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_document_chunks_document_provider
    ON document_chunks (document_id, embedding_provider)
    WHERE embedding IS NOT NULL;
