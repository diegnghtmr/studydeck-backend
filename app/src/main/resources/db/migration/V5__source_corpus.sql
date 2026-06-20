-- V5: AI/RAG — Source document corpus, chunks, embeddings, ingest jobs.
-- All corpus data is isolated by owner_id (enforced by FK + application filters).
-- Embedding dimension: 384 (all-MiniLM-L6-v2 via Spring AI Transformers/ONNX).
-- Each embedding row records the model name + dimensions to prevent mixed-dim indexing.

-- source_document: uploaded or pasted text registered for RAG ingestion.
CREATE TABLE source_document (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    title             TEXT        NOT NULL CHECK (length(title) BETWEEN 1 AND 255),
    source_type       TEXT        NOT NULL
                                  CHECK (source_type IN ('upload', 'pasted-text', 'url', 'import-json')),
    mime_type         TEXT,
    original_filename TEXT,
    text_content      TEXT,
    external_url      TEXT,
    size_bytes        BIGINT,
    ingest_status     TEXT        NOT NULL DEFAULT 'PENDING'
                                  CHECK (ingest_status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    metadata          JSONB       NOT NULL DEFAULT '{}',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_source_document_owner ON source_document (owner_id, created_at DESC);
CREATE INDEX idx_source_document_status ON source_document (owner_id, ingest_status);
CREATE INDEX idx_source_document_metadata ON source_document USING GIN (metadata);

COMMENT ON TABLE source_document IS
    'Source text documents registered by a user for RAG ingestion. All data is owner-scoped.';
COMMENT ON COLUMN source_document.ingest_status IS
    'PENDING = registered but not yet ingested; RUNNING = ETL in progress; COMPLETED = chunks+embeddings stored; FAILED = ETL error.';

-- document_chunk: text split produced by the ETL pipeline (TokenTextSplitter).
CREATE TABLE document_chunk (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID        NOT NULL REFERENCES source_document(id) ON DELETE CASCADE,
    owner_id    UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    ordinal     INTEGER     NOT NULL CHECK (ordinal >= 0),
    content     TEXT        NOT NULL,
    token_count INTEGER,
    metadata    JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, ordinal)
);

CREATE INDEX idx_document_chunk_document ON document_chunk (document_id, ordinal);
CREATE INDEX idx_document_chunk_owner ON document_chunk (owner_id);

COMMENT ON TABLE document_chunk IS
    'Text chunks produced by splitting a source_document during ETL ingest.';

-- embedding_record: vector embedding for one chunk.
-- Stores embedding_model + dimensions per row to guard against mixed-dim indexing.
-- Spring AI pgvector VectorStore manages the actual vector column in its own table;
-- this table stores metadata (model, dims) so queries can validate dimension consistency.
CREATE TABLE embedding_record (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id        UUID        NOT NULL REFERENCES document_chunk(id) ON DELETE CASCADE,
    owner_id        UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    embedding_model TEXT        NOT NULL,
    dimensions      INTEGER     NOT NULL CHECK (dimensions > 0),
    provider        TEXT        NOT NULL DEFAULT 'transformers',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (chunk_id)
);

CREATE INDEX idx_embedding_record_chunk ON embedding_record (chunk_id);
CREATE INDEX idx_embedding_record_owner ON embedding_record (owner_id);
CREATE INDEX idx_embedding_record_model ON embedding_record (embedding_model, dimensions);

COMMENT ON TABLE embedding_record IS
    'Metadata for each vector embedding: which model/dims produced it. Prevents mixed-dimension ANN index errors.';
COMMENT ON COLUMN embedding_record.embedding_model IS
    'Model identifier, e.g. all-MiniLM-L6-v2 (384 dims) or text-embedding-3-large (1536 dims).';
COMMENT ON COLUMN embedding_record.dimensions IS
    'Embedding vector dimensions — must match the pgvector column dimension in vector_store.';

-- ingest_job: tracks async ETL ingestion of a document (202 Accepted pattern).
CREATE TABLE ingest_job (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID        NOT NULL REFERENCES source_document(id) ON DELETE CASCADE,
    owner_id    UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    status      TEXT        NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    error_message TEXT,
    chunks_produced INTEGER,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ingest_job_document ON ingest_job (document_id);
CREATE INDEX idx_ingest_job_owner ON ingest_job (owner_id, created_at DESC);
CREATE INDEX idx_ingest_job_status ON ingest_job (status) WHERE status IN ('PENDING', 'RUNNING');

COMMENT ON TABLE ingest_job IS
    'Async ETL ingest job record. POST /v1/documents/{id}/ingest returns 202 + IngestJob; status progresses PENDING→RUNNING→COMPLETED|FAILED.';

-- Spring AI pgvector VectorStore table (must match the PgVectorStore bean configuration).
-- Using NONE index type for MVP (no HNSW tuning yet — exact cosine search).
-- dimension=384 matches all-MiniLM-L6-v2 (default local ONNX embedding model).
-- The vector_store table is owned by Spring AI; we never write to it directly from JPA.
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    embedding vector(384)
);

CREATE INDEX idx_vector_store_embedding ON vector_store USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

COMMENT ON TABLE vector_store IS
    'Spring AI pgvector VectorStore table. Managed by Spring AI infrastructure adapter. dimension=384 for all-MiniLM-L6-v2.';
COMMENT ON COLUMN vector_store.metadata IS
    'JSON metadata including ownerId, documentId, chunkId, sourceType for owner-scoped retrieval.';
