-- V2: Notes, Cards, and Audit Event tables.
-- NoteContent and CardPayload are stored as JSONB.
-- Tags on notes are stored as text[].

-- note: study note belonging to a deck
CREATE TABLE note (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    deck_id    UUID        NOT NULL REFERENCES deck(id) ON DELETE CASCADE,
    note_type  TEXT        NOT NULL
                           CHECK (note_type IN ('BASIC', 'REVERSED', 'CLOZE', 'MULTIPLE_CHOICE', 'FREE_TEXT')),
    content    JSONB       NOT NULL,
    tags       TEXT[]      NOT NULL DEFAULT '{}',
    version    INTEGER     NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_note_deck_id ON note (deck_id);
CREATE INDEX idx_note_note_type ON note (deck_id, note_type);
CREATE INDEX idx_note_tags ON note USING GIN (tags);
CREATE INDEX idx_note_created_at ON note (created_at);

-- card: flashcard derived from a note
-- prompt_payload and answer_payload are typed JSONB (CardPayload variants)
CREATE TABLE card (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    note_id         UUID        NOT NULL REFERENCES note(id) ON DELETE CASCADE,
    note_type       TEXT        NOT NULL
                                CHECK (note_type IN ('BASIC', 'REVERSED', 'CLOZE', 'MULTIPLE_CHOICE', 'FREE_TEXT')),
    card_variant    TEXT        NOT NULL,
    ordinal         INTEGER     NOT NULL DEFAULT 0,
    prompt_payload  JSONB       NOT NULL,
    answer_payload  JSONB       NOT NULL,
    suspended       BOOLEAN     NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_card_note_id ON card (note_id);
CREATE INDEX idx_card_suspended ON card (suspended);

-- audit_event: immutable audit log of domain actions
CREATE TABLE audit_event (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID        NOT NULL,
    action      TEXT        NOT NULL,
    target_type TEXT        NOT NULL,
    target_id   TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_event_actor_id ON audit_event (actor_id);
CREATE INDEX idx_audit_event_target ON audit_event (target_type, target_id);
CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at);

COMMENT ON TABLE note IS 'Study notes. Content stored as JSONB per note type.';
COMMENT ON TABLE card IS 'Flashcards derived from notes. Prompt/answer stored as JSONB.';
COMMENT ON TABLE audit_event IS 'Immutable domain audit log.';
