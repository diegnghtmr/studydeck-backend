-- V8: Extended user preferences — desired retention, new cards/day, language, timezone.

ALTER TABLE user_account ADD COLUMN desired_retention NUMERIC(3,2) NOT NULL DEFAULT 0.90
    CHECK (desired_retention BETWEEN 0.50 AND 0.99);

ALTER TABLE user_account ADD COLUMN new_cards_per_day INTEGER NOT NULL DEFAULT 10
    CHECK (new_cards_per_day BETWEEN 0 AND 999);

ALTER TABLE user_account ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'en'
    CHECK (language IN ('en','es','fr','pt'));

ALTER TABLE user_account ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';

COMMENT ON COLUMN user_account.desired_retention IS 'Target retention fraction for spaced repetition (0.50..0.99, default 0.90).';
COMMENT ON COLUMN user_account.new_cards_per_day IS 'Maximum new cards introduced per day (0..999, default 10).';
COMMENT ON COLUMN user_account.language IS 'UI language preference (ISO 639-1: en, es, fr, pt).';
COMMENT ON COLUMN user_account.timezone IS 'IANA timezone for daily boundaries (default UTC).';
