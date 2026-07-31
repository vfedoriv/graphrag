CREATE TABLE advanced_search_run (
    id varchar(255) PRIMARY KEY,
    knowledge_base_id varchar(255) NOT NULL,
    query_text varchar(4000) NOT NULL,
    status varchar(32) NOT NULL,
    stage varchar(32) NOT NULL,
    requested_evidence integer NOT NULL,
    include_evidence_text boolean NOT NULL,
    settings_snapshot_json jsonb NOT NULL,
    completed_branches integer NOT NULL DEFAULT 0,
    total_branches integer NOT NULL DEFAULT 3,
    evidence_count integer NOT NULL DEFAULT 0,
    cancellation_requested_at timestamptz,
    failure_category varchar(80),
    claimed_by varchar(255),
    claimed_at timestamptz,
    deadline_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    expires_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT advanced_search_run_kb_fk FOREIGN KEY (knowledge_base_id)
        REFERENCES knowledge_base (id) ON DELETE CASCADE,
    CONSTRAINT advanced_search_run_status_check CHECK
        (status IN ('QUEUED','RUNNING','COMPLETED','PARTIAL','FAILED','CANCELLED','INTERRUPTED')),
    CONSTRAINT advanced_search_run_stage_check CHECK
        (stage IN ('QUEUED','RETRIEVAL','RANKING','TERMINAL')),
    CONSTRAINT advanced_search_run_bounds_check CHECK
        (requested_evidence BETWEEN 1 AND 20 AND completed_branches >= 0
         AND total_branches BETWEEN 1 AND 8 AND evidence_count >= 0),
    CONSTRAINT advanced_search_run_terminal_times_check CHECK
        ((status IN ('COMPLETED','PARTIAL','FAILED','CANCELLED','INTERRUPTED')) =
         (completed_at IS NOT NULL AND expires_at IS NOT NULL))
);

CREATE INDEX advanced_search_run_kb_history_idx
    ON advanced_search_run (knowledge_base_id, created_at DESC, id DESC);
CREATE INDEX advanced_search_run_kb_status_history_idx
    ON advanced_search_run (knowledge_base_id, status, created_at DESC, id DESC);
CREATE INDEX advanced_search_run_recovery_idx
    ON advanced_search_run (status, created_at)
    WHERE status IN ('QUEUED','RUNNING');
CREATE INDEX advanced_search_run_expiry_idx
    ON advanced_search_run (expires_at, id)
    WHERE status IN ('COMPLETED','PARTIAL','FAILED','CANCELLED','INTERRUPTED');

CREATE TABLE advanced_search_attempt (
    id varchar(255) PRIMARY KEY,
    run_id varchar(255) NOT NULL,
    round_number integer NOT NULL,
    subquery_id varchar(255),
    retriever varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    candidate_count integer NOT NULL DEFAULT 0,
    latency_ms bigint NOT NULL DEFAULT 0,
    failure_category varchar(80),
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT advanced_search_attempt_run_fk FOREIGN KEY (run_id)
        REFERENCES advanced_search_run (id) ON DELETE CASCADE,
    CONSTRAINT advanced_search_attempt_bounds_check CHECK
        (round_number > 0 AND candidate_count >= 0 AND latency_ms >= 0),
    CONSTRAINT advanced_search_attempt_retriever_check CHECK (retriever IN ('DENSE','LEXICAL','METADATA','GRAPH')),
    CONSTRAINT advanced_search_attempt_status_check CHECK (status IN ('COMPLETED','FAILED','DEADLINE_EXCEEDED','CANCELLED')),
    CONSTRAINT advanced_search_attempt_identity_unique
        UNIQUE NULLS NOT DISTINCT (run_id, round_number, subquery_id, retriever)
);
CREATE INDEX advanced_search_attempt_run_idx ON advanced_search_attempt (run_id, created_at, id);

CREATE TABLE advanced_search_result (
    run_id varchar(255) PRIMARY KEY,
    payload_version integer NOT NULL,
    result_json jsonb NOT NULL,
    evidence_count integer NOT NULL,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT advanced_search_result_run_fk FOREIGN KEY (run_id)
        REFERENCES advanced_search_run (id) ON DELETE CASCADE,
    CONSTRAINT advanced_search_result_version_check CHECK (payload_version = 1),
    CONSTRAINT advanced_search_result_bounds_check CHECK (evidence_count BETWEEN 0 AND 20),
    CONSTRAINT advanced_search_result_object_check CHECK (jsonb_typeof(result_json) = 'object')
);
