-- V7: Per-user daily study goal (cards/day), synced across devices.
-- Defaults to 40 so existing users keep the previous fixed client default.

ALTER TABLE user_account ADD COLUMN daily_goal INTEGER NOT NULL DEFAULT 40
    CHECK (daily_goal BETWEEN 1 AND 1000);

COMMENT ON COLUMN user_account.daily_goal IS 'User-chosen daily study target (cards/day), 1..1000.';
