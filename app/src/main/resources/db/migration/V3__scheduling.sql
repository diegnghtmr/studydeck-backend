-- V3: Scheduling tables — card schedule state, review logs, review sessions, scheduler presets.
-- All PKs are UUID. FKs reference existing tables.
-- JSONB is used for the FSRS weight vector (variable-length double array, rarely queried).
-- Critical query indexes: (owner_id, due_at) for due-cards, (card_id) for state lookups,
-- (deck_id, reviewed_at) for stats/history.

-- card_schedule_state: 1:1 with card; holds the current FSRS/SM-2 state for a card.
-- When a card has no row it is treated as NEW and due immediately (backfill via application layer).
CREATE TABLE card_schedule_state (
    card_id             UUID        PRIMARY KEY REFERENCES card(id) ON DELETE CASCADE,
    owner_id            UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    deck_id             UUID        NOT NULL REFERENCES deck(id) ON DELETE CASCADE,
    algorithm           TEXT        NOT NULL CHECK (algorithm IN ('FSRS', 'SM2')),
    state               TEXT        NOT NULL CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    stability           DOUBLE PRECISION NOT NULL DEFAULT 0,
    difficulty          DOUBLE PRECISION NOT NULL DEFAULT 5,
    desired_retention   DOUBLE PRECISION NOT NULL DEFAULT 0.9,
    reps                INTEGER     NOT NULL DEFAULT 0,
    lapses              INTEGER     NOT NULL DEFAULT 0,
    scheduled_days      INTEGER     NOT NULL DEFAULT 0,
    due_at              TIMESTAMPTZ NOT NULL,
    last_reviewed_at    TIMESTAMPTZ
);

-- Primary access patterns:
-- 1. Due cards for a user+deck: (owner_id, deck_id, due_at)
-- 2. State lookup by card: card_id (PK)
CREATE INDEX idx_css_owner_due ON card_schedule_state (owner_id, due_at);
CREATE INDEX idx_css_owner_deck_due ON card_schedule_state (owner_id, deck_id, due_at);

COMMENT ON TABLE card_schedule_state IS 'Current FSRS/SM-2 state for each card. 1:1 with card row.';

-- review_log: immutable append-only log of every review event.
-- Joins back to card via card_id; deck_id is denormalized for efficient stats queries.
CREATE TABLE review_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    session_id      UUID,  -- nullable: review may happen outside a session
    card_id         UUID        NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    deck_id         UUID        NOT NULL REFERENCES deck(id) ON DELETE CASCADE,
    rating          TEXT        NOT NULL CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    state_before    TEXT        NOT NULL CHECK (state_before IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    elapsed_days    INTEGER     NOT NULL DEFAULT 0,
    scheduled_days  INTEGER     NOT NULL DEFAULT 0,
    response_time_ms INTEGER,
    reviewed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_log_owner ON review_log (owner_id, reviewed_at DESC);
CREATE INDEX idx_review_log_card ON review_log (card_id, reviewed_at DESC);
CREATE INDEX idx_review_log_deck ON review_log (deck_id, reviewed_at DESC);
CREATE INDEX idx_review_log_session ON review_log (session_id) WHERE session_id IS NOT NULL;

COMMENT ON TABLE review_log IS 'Immutable review history. Append-only; no updates after insert.';

-- review_session: lightweight envelope for a study session.
CREATE TABLE review_session (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    deck_id         UUID        REFERENCES deck(id) ON DELETE SET NULL,
    max_cards       INTEGER     NOT NULL DEFAULT 20,
    status          TEXT        NOT NULL DEFAULT 'started'
                                CHECK (status IN ('started', 'completed', 'abandoned')),
    presented_count INTEGER     NOT NULL DEFAULT 0,
    answered_count  INTEGER     NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ
);

CREATE INDEX idx_review_session_owner ON review_session (owner_id, started_at DESC);

COMMENT ON TABLE review_session IS 'Study session envelope. Card selection driven by card_schedule_state.';

-- scheduler_preset: user-defined or system presets for algorithm + hyperparameters.
-- weights is stored as JSONB (array of doubles); null means use algorithm defaults.
CREATE TABLE scheduler_preset (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID        NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    name              TEXT        NOT NULL,
    algorithm         TEXT        NOT NULL CHECK (algorithm IN ('FSRS', 'SM2')),
    desired_retention DOUBLE PRECISION NOT NULL DEFAULT 0.9
                      CHECK (desired_retention >= 0.70 AND desired_retention <= 0.99),
    weights           JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_scheduler_preset_owner ON scheduler_preset (owner_id);

COMMENT ON TABLE scheduler_preset IS 'Named FSRS/SM-2 presets with optional custom weights.';

-- deck_preset: links a deck to a custom scheduler preset (optional, deck uses system default otherwise).
CREATE TABLE deck_preset (
    deck_id     UUID PRIMARY KEY REFERENCES deck(id) ON DELETE CASCADE,
    preset_id   UUID NOT NULL REFERENCES scheduler_preset(id) ON DELETE CASCADE
);

COMMENT ON TABLE deck_preset IS 'Optional override: deck -> custom scheduler preset. Absent rows use FSRS default.';
