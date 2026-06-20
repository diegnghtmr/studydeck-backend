-- V4: Import/Export tables — import_job and content hash index on note for dedup.
-- import_job: tracks an import operation (1 job per POST /v1/imports/flashcards).
-- note gets a content_hash column for exact-match dedup.

-- 1. Add content_hash to note for dedup (SHA-256 hex of normalized content JSON, per note type)
ALTER TABLE note ADD COLUMN content_hash TEXT;

CREATE INDEX idx_note_content_hash ON note (deck_id, note_type, content_hash)
    WHERE content_hash IS NOT NULL;

COMMENT ON COLUMN note.content_hash IS
    'SHA-256 hex of normalized content JSON, keyed by (deck_id, note_type). Used for exact-match dedup during import.';

-- 2. import_job: one row per executed import operation
CREATE TABLE import_job (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    deck_id         UUID        REFERENCES deck(id) ON DELETE SET NULL,
    schema_version  TEXT        NOT NULL DEFAULT '1.0',
    status          TEXT        NOT NULL DEFAULT 'completed'
                                CHECK (status IN ('completed', 'partial', 'failed')),
    imported_notes  INTEGER     NOT NULL DEFAULT 0,
    imported_cards  INTEGER     NOT NULL DEFAULT 0,
    duplicate_notes INTEGER     NOT NULL DEFAULT 0,
    rejected_notes  INTEGER     NOT NULL DEFAULT 0,
    warnings        TEXT[],
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_import_job_owner ON import_job (owner_id, created_at DESC);
CREATE INDEX idx_import_job_deck ON import_job (deck_id) WHERE deck_id IS NOT NULL;

COMMENT ON TABLE import_job IS
    'Tracks each import operation (POST /v1/imports/flashcards). One row per executed import.';
