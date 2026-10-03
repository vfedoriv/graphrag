ALTER TABLE ai_profile
    ADD COLUMN structured_output_mode varchar(32) NOT NULL DEFAULT 'PORTABLE',
    ADD CONSTRAINT ai_profile_structured_output_mode_check
        CHECK (structured_output_mode IN ('PORTABLE', 'NATIVE_JSON_SCHEMA'));
