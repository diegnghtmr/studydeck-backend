-- V10: BYOK AI provider configs per user.
-- API key stored encrypted at rest (AES-256-GCM, IV-prefixed Base64).

CREATE TABLE user_ai_provider (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id           UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    label              TEXT        NOT NULL CHECK (length(label) BETWEEN 1 AND 80),
    base_url           TEXT        NOT NULL,
    model              TEXT        NOT NULL,
    api_key_ciphertext TEXT        NOT NULL,   -- Base64( IV(12) || GCM(ct+tag) ); never returned to clients
    key_hint           TEXT        NOT NULL,   -- non-secret masked display hint (first4…last4 or •••••)
    active             BOOLEAN     NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_ai_provider_owner ON user_ai_provider (owner_id);

-- Partial unique index: at most one active provider per owner at the DB level.
-- This is the race backstop complementing the application-layer deactivate-then-activate logic.
CREATE UNIQUE INDEX uq_user_ai_provider_active_owner
    ON user_ai_provider (owner_id)
    WHERE active = true;

COMMENT ON TABLE user_ai_provider IS 'BYOK AI provider configs, API key encrypted at rest (AES-256-GCM, IV-prefixed Base64).';
COMMENT ON COLUMN user_ai_provider.api_key_ciphertext IS 'Base64(IV||GCM(ciphertext+tag)); never returned to clients.';
COMMENT ON COLUMN user_ai_provider.key_hint IS 'Non-secret masked hint for display: first4…last4 of the original plaintext key, or ••••• for keys shorter than 9 chars.';
