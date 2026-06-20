-- V1: Core schema bootstrap
-- Creates pgvector extension and minimal user_account + deck tables.
-- Full schema (notes, cards, reviews, embeddings, etc.) follows in P1+ migrations.

-- pgvector extension for semantic search (RAG support in later phases)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- user_account: platform users (authentication managed externally via OIDC)
CREATE TABLE user_account (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email        TEXT        NOT NULL UNIQUE,
    display_name TEXT        NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_account_email ON user_account (email);
CREATE INDEX idx_user_account_status ON user_account (status);

-- deck: study deck owned by a user
-- Aligned to OpenAPI contract: title (1-120), description (max 1000),
-- tags stored as text[], defaultDesiredRetention (0.70-0.99), archived flag.
-- No "name" or "visibility" columns (removed per contract reconciliation).
CREATE TABLE deck (
    id                       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id                 UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    title                    TEXT        NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
    description              TEXT        CHECK (description IS NULL OR length(description) <= 1000),
    tags                     TEXT[]      NOT NULL DEFAULT '{}',
    default_desired_retention DOUBLE PRECISION NOT NULL DEFAULT 0.9
                             CHECK (default_desired_retention >= 0.70 AND default_desired_retention <= 0.99),
    archived                 BOOLEAN     NOT NULL DEFAULT false,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_deck_owner_id ON deck (owner_id);
CREATE INDEX idx_deck_created_at ON deck (created_at);
CREATE INDEX idx_deck_archived ON deck (owner_id, archived);

COMMENT ON TABLE user_account IS 'Platform users. Auth handled by external OIDC provider.';
COMMENT ON TABLE deck IS 'Study decks. Parent of notes and cards.';
