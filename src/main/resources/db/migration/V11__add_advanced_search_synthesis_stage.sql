ALTER TABLE advanced_search_run
    DROP CONSTRAINT advanced_search_run_stage_check;

ALTER TABLE advanced_search_run
    ADD CONSTRAINT advanced_search_run_stage_check CHECK
        (stage IN ('QUEUED','RETRIEVAL','RANKING','SYNTHESIS','TERMINAL'));
