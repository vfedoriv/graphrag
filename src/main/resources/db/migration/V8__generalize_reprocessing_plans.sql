ALTER TABLE schema_reprocessing_plan
    ADD COLUMN reason varchar(40) NOT NULL DEFAULT 'SCHEMA_ACTIVATION',
    ADD COLUMN selection_mode varchar(40),
    ADD COLUMN expected_chunker_revision varchar(80),
    ADD COLUMN target_snapshot_json text,
    ADD COLUMN embedding_space_id varchar(80);

ALTER TABLE schema_reprocessing_plan
    ALTER COLUMN draft_id DROP NOT NULL,
    ALTER COLUMN schema_id DROP NOT NULL,
    ALTER COLUMN schema_content_hash DROP NOT NULL;

ALTER TABLE schema_reprocessing_plan
    ADD CONSTRAINT schema_reprocessing_plan_reason_check
        CHECK (reason IN ('SCHEMA_ACTIVATION', 'CHUNK_STRATEGY_MIGRATION')),
    ADD CONSTRAINT schema_reprocessing_plan_selection_check
        CHECK (selection_mode IS NULL OR selection_mode IN ('OUTDATED_STRATEGY', 'DOCUMENT_IDS', 'ALL')),
    ADD CONSTRAINT schema_reprocessing_plan_reason_shape_check CHECK (
        (reason = 'SCHEMA_ACTIVATION'
            AND draft_id IS NOT NULL
            AND schema_id IS NOT NULL
            AND schema_content_hash IS NOT NULL
            AND selection_mode IS NULL
            AND expected_chunker_revision IS NULL
            AND target_snapshot_json IS NULL)
        OR
        (reason = 'CHUNK_STRATEGY_MIGRATION'
            AND selection_mode IS NOT NULL
            AND expected_chunker_revision IS NOT NULL
            AND target_snapshot_json IS NOT NULL
            AND embedding_space_id IS NOT NULL
            AND schema_id IS NOT NULL
            AND schema_content_hash IS NOT NULL)
    );

CREATE UNIQUE INDEX schema_reprocessing_plan_one_destructive_active_idx
    ON schema_reprocessing_plan (knowledge_base_id)
    WHERE status IN ('QUEUED', 'RUNNING');

ALTER TABLE schema_reprocessing_item
    DROP CONSTRAINT schema_reprocessing_item_status_check,
    DROP CONSTRAINT schema_reprocessing_item_terminal_check;

ALTER TABLE schema_reprocessing_item
    ADD CONSTRAINT schema_reprocessing_item_status_check CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'STALE_SOURCE',
            'BLOCKED_TARGET_CHANGED', 'BLOCKED', 'INTERRUPTED', 'SKIPPED')
    ),
    ADD CONSTRAINT schema_reprocessing_item_terminal_check CHECK (
        (status = 'QUEUED' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN (
            'SUCCEEDED', 'FAILED', 'STALE_SOURCE', 'BLOCKED_TARGET_CHANGED',
            'BLOCKED', 'INTERRUPTED', 'SKIPPED'
        ) AND completed_at IS NOT NULL)
    );
