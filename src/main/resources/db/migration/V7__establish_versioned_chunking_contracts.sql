ALTER TABLE ai_profile
    ADD COLUMN tokenizer_id varchar(255);

ALTER TABLE document_processing_run
    ADD COLUMN chunk_strategy varchar(255),
    ADD COLUMN chunk_strategy_revision varchar(255),
    ADD COLUMN chunk_settings_hash varchar(64),
    ADD COLUMN tokenizer_id varchar(255),
    ADD COLUMN tokenizer_revision varchar(255),
    ADD COLUMN token_count_mode varchar(32),
    ADD COLUMN effective_chunker_revision varchar(80);

ALTER TABLE document_processing_run
    ADD CONSTRAINT document_processing_run_chunk_settings_hash_check
        CHECK (chunk_settings_hash IS NULL OR chunk_settings_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT document_processing_run_count_mode_check
        CHECK (token_count_mode IS NULL OR token_count_mode IN ('EXACT', 'CONSERVATIVE'));
