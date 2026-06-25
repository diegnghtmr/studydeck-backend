ALTER TABLE user_account
    ADD COLUMN scheduler_algorithm VARCHAR(8) NOT NULL DEFAULT 'FSRS'
        CHECK (scheduler_algorithm IN ('FSRS','SM2'));
COMMENT ON COLUMN user_account.scheduler_algorithm IS 'SRS algorithm preference: FSRS or SM2';
