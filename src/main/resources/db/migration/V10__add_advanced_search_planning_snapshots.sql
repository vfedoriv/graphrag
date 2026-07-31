ALTER TABLE advanced_search_run
    ADD COLUMN active_ai_profile_id varchar(255),
    ADD COLUMN schema_definition_id varchar(255),
    ADD COLUMN schema_content_hash varchar(64),
    ADD COLUMN schema_snapshot_json jsonb;

ALTER TABLE advanced_search_run
    ADD CONSTRAINT advanced_search_run_schema_snapshot_check CHECK (
        (schema_definition_id IS NULL AND schema_content_hash IS NULL AND schema_snapshot_json IS NULL)
        OR
        (schema_definition_id IS NOT NULL AND schema_content_hash IS NOT NULL AND schema_snapshot_json IS NOT NULL)
    );
