CREATE TABLE schema_draft (
    id varchar(255) PRIMARY KEY,
    knowledge_base_id varchar(255) NOT NULL,
    target_name varchar(255) NOT NULL,
    target_version integer NOT NULL,
    base_schema_id varchar(255),
    status varchar(32) NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    guidance_revision bigint NOT NULL DEFAULT 0,
    guidance_json text NOT NULL,
    guidance_fingerprint varchar(64) NOT NULL,
    current_aggregate_id varchar(255),
    running_analysis_run_id varchar(255),
    publication_schema_id varchar(255),
    publication_content_hash varchar(64),
    active_ai_profile_id varchar(255) NOT NULL,
    active_ai_profile_revision bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_kb_fk
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_base_schema_fk
        FOREIGN KEY (base_schema_id) REFERENCES schema_definition (id),
    CONSTRAINT schema_draft_publication_schema_fk
        FOREIGN KEY (publication_schema_id) REFERENCES schema_definition (id),
    CONSTRAINT schema_draft_ai_profile_fk
        FOREIGN KEY (active_ai_profile_id) REFERENCES ai_profile (id),
    CONSTRAINT schema_draft_status_check CHECK (status IN ('OPEN', 'PUBLISHED')),
    CONSTRAINT schema_draft_target_version_check CHECK (target_version > 0),
    CONSTRAINT schema_draft_revision_check CHECK (revision >= 0 AND guidance_revision >= 0),
    CONSTRAINT schema_draft_profile_revision_check CHECK (active_ai_profile_revision >= 0)
);

ALTER TABLE schema_draft
    ADD CONSTRAINT schema_draft_id_owner_unique UNIQUE (id, knowledge_base_id);

CREATE TABLE schema_draft_source (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    knowledge_base_id varchar(255) NOT NULL,
    type varchar(32) NOT NULL,
    status varchar(32) NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    document_id varchar(255),
    name varchar(1024),
    content_type varchar(255),
    size_bytes bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    content_uri text,
    analyzed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_source_owner_fk
        FOREIGN KEY (draft_id, knowledge_base_id)
        REFERENCES schema_draft (id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_source_document_fk
        FOREIGN KEY (document_id) REFERENCES document_upload (id),
    CONSTRAINT schema_draft_source_type_check CHECK (type IN ('DOCUMENT', 'FILE', 'TEXT')),
    CONSTRAINT schema_draft_source_status_check
        CHECK (status IN ('ACTIVE', 'STALE', 'UNAVAILABLE', 'INACTIVE')),
    CONSTRAINT schema_draft_source_revision_check CHECK (revision >= 0),
    CONSTRAINT schema_draft_source_size_check CHECK (size_bytes >= 0),
    CONSTRAINT schema_draft_source_sha256_check CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT schema_draft_source_document_shape_check CHECK (
        (type = 'DOCUMENT' AND document_id IS NOT NULL AND content_uri IS NULL)
        OR (type IN ('FILE', 'TEXT') AND document_id IS NULL)
    )
);

ALTER TABLE schema_draft_source
    ADD CONSTRAINT schema_draft_source_id_owner_unique UNIQUE (id, draft_id);

CREATE TABLE schema_draft_source_revision (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    source_id varchar(255) NOT NULL,
    revision bigint NOT NULL,
    status varchar(32) NOT NULL,
    sha256 varchar(64) NOT NULL,
    document_id varchar(255),
    content_uri text,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_source_revision_draft_fk
        FOREIGN KEY (draft_id) REFERENCES schema_draft (id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_source_revision_source_fk
        FOREIGN KEY (source_id, draft_id)
        REFERENCES schema_draft_source (id, draft_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_source_revision_document_fk
        FOREIGN KEY (document_id) REFERENCES document_upload (id),
    CONSTRAINT schema_draft_source_revision_unique UNIQUE (source_id, revision),
    CONSTRAINT schema_draft_source_revision_status_check
        CHECK (status IN ('ACTIVE', 'STALE', 'UNAVAILABLE', 'INACTIVE')),
    CONSTRAINT schema_draft_source_revision_number_check CHECK (revision >= 0),
    CONSTRAINT schema_draft_source_revision_sha256_check CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE schema_draft_analysis_run (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    knowledge_base_id varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    draft_revision bigint NOT NULL,
    guidance_revision bigint NOT NULL,
    guidance_fingerprint varchar(64) NOT NULL,
    source_snapshot_json text NOT NULL,
    source_membership_fingerprint varchar(64) NOT NULL,
    ai_profile_id varchar(255) NOT NULL,
    ai_profile_revision bigint NOT NULL,
    configured_timeout_seconds integer NOT NULL,
    configured_sdk_max_retries integer NOT NULL,
    prompt_revision varchar(255) NOT NULL,
    candidate_revision varchar(255) NOT NULL,
    settings_fingerprint varchar(64),
    discovery_max_concurrency integer,
    discovery_source_timeout_millis bigint,
    discovery_request_timeout_millis bigint,
    snapshot_fingerprint varchar(64) NOT NULL,
    retry_of_run_id varchar(255),
    claimed_by varchar(255),
    claimed_at timestamptz,
    total_sources integer NOT NULL DEFAULT 0,
    succeeded_sources integer NOT NULL DEFAULT 0,
    failed_sources integer NOT NULL DEFAULT 0,
    current_result boolean NOT NULL DEFAULT false,
    aggregate_revision_id varchar(255),
    failure_category varchar(255),
    retryable boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_analysis_run_owner_fk
        FOREIGN KEY (draft_id, knowledge_base_id)
        REFERENCES schema_draft (id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_analysis_run_ai_profile_fk
        FOREIGN KEY (ai_profile_id) REFERENCES ai_profile (id),
    CONSTRAINT schema_draft_analysis_run_retry_fk
        FOREIGN KEY (retry_of_run_id) REFERENCES schema_draft_analysis_run (id) ON DELETE SET NULL,
    CONSTRAINT schema_draft_analysis_run_status_check
        CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT schema_draft_analysis_run_counts_check CHECK (
        draft_revision >= 0 AND guidance_revision >= 0 AND ai_profile_revision >= 0
        AND configured_timeout_seconds > 0 AND configured_sdk_max_retries >= 0
        AND total_sources >= 0 AND succeeded_sources >= 0 AND failed_sources >= 0
        AND succeeded_sources + failed_sources <= total_sources
    ),
    CONSTRAINT schema_draft_analysis_run_terminal_check CHECK (
        (status = 'RUNNING' AND completed_at IS NULL)
        OR (status IN ('COMPLETED', 'PARTIAL', 'FAILED') AND completed_at IS NOT NULL)
    ),
    CONSTRAINT schema_draft_analysis_run_claim_check CHECK (
        (claimed_by IS NULL AND claimed_at IS NULL) OR (claimed_by IS NOT NULL AND claimed_at IS NOT NULL)
    )
);

ALTER TABLE schema_draft_analysis_run
    ADD CONSTRAINT schema_draft_analysis_run_id_owner_unique UNIQUE (id, draft_id);

CREATE TABLE schema_draft_source_result (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    run_id varchar(255) NOT NULL,
    source_id varchar(255) NOT NULL,
    source_revision bigint NOT NULL,
    source_sha256 varchar(64),
    reuse_key varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    reused boolean NOT NULL DEFAULT false,
    candidates_json text,
    aliases_json text,
    failure_category varchar(255),
    failure_code varchar(255),
    retryable boolean NOT NULL DEFAULT false,
    chunk_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_source_result_draft_fk
        FOREIGN KEY (draft_id) REFERENCES schema_draft (id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_source_result_run_fk
        FOREIGN KEY (run_id, draft_id)
        REFERENCES schema_draft_analysis_run (id, draft_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_source_result_source_fk
        FOREIGN KEY (source_id, draft_id)
        REFERENCES schema_draft_source (id, draft_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_source_result_source_revision_fk
        FOREIGN KEY (source_id, source_revision)
        REFERENCES schema_draft_source_revision (source_id, revision),
    CONSTRAINT schema_draft_source_result_run_source_unique UNIQUE (run_id, source_id, source_revision),
    CONSTRAINT schema_draft_source_result_run_reuse_unique UNIQUE (run_id, reuse_key),
    CONSTRAINT schema_draft_source_result_status_check
        CHECK (status IN ('SUCCEEDED', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT schema_draft_source_result_revision_check CHECK (source_revision >= 0),
    CONSTRAINT schema_draft_source_result_chunk_check CHECK (chunk_count >= 0),
    CONSTRAINT schema_draft_source_result_terminal_check CHECK (completed_at IS NOT NULL)
);

CREATE TABLE schema_draft_aggregate_revision (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    run_id varchar(255) NOT NULL,
    revision bigint NOT NULL,
    candidates_json text NOT NULL,
    conflicts_json text NOT NULL,
    warnings_json text NOT NULL,
    schema_json text NOT NULL,
    content_hash varchar(64) NOT NULL,
    diff_baseline_type varchar(32),
    diff_baseline_id varchar(255),
    diff_baseline_schema_json text,
    diff_baseline_content_hash varchar(64),
    partial boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_aggregate_draft_fk
        FOREIGN KEY (draft_id) REFERENCES schema_draft (id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_aggregate_run_fk
        FOREIGN KEY (run_id, draft_id)
        REFERENCES schema_draft_analysis_run (id, draft_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_aggregate_revision_unique UNIQUE (draft_id, revision),
    CONSTRAINT schema_draft_aggregate_run_unique UNIQUE (run_id),
    CONSTRAINT schema_draft_aggregate_revision_check CHECK (revision >= 0),
    CONSTRAINT schema_draft_aggregate_baseline_check
        CHECK (diff_baseline_type IS NULL
            OR diff_baseline_type IN ('BASE_SCHEMA', 'PREVIOUS_AGGREGATE', 'EMPTY'))
);

ALTER TABLE schema_draft_aggregate_revision
    ADD CONSTRAINT schema_draft_aggregate_id_owner_unique UNIQUE (id, draft_id);

CREATE TABLE schema_draft_conflict (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    aggregate_revision_id varchar(255) NOT NULL,
    type varchar(64) NOT NULL,
    coordinate text NOT NULL,
    semantic_key varchar(64),
    alternatives_json text NOT NULL,
    evidence_json text NOT NULL,
    resolved boolean NOT NULL DEFAULT false,
    selected_alternative text,
    custom_resolution_json text,
    created_at timestamptz NOT NULL,
    resolved_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_conflict_draft_fk
        FOREIGN KEY (draft_id) REFERENCES schema_draft (id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_conflict_aggregate_fk
        FOREIGN KEY (aggregate_revision_id, draft_id)
        REFERENCES schema_draft_aggregate_revision (id, draft_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_conflict_coordinate_unique
        UNIQUE (aggregate_revision_id, coordinate),
    CONSTRAINT schema_draft_conflict_type_check
        CHECK (type IN ('TYPE', 'KEY', 'ALIAS', 'RELATIONSHIP_NAME', 'RELATIONSHIP_DIRECTION',
            'PINNED_DEFINITION', 'COORDINATE')),
    CONSTRAINT schema_draft_conflict_resolution_check CHECK (
        (NOT resolved AND resolved_at IS NULL)
        OR (resolved AND resolved_at IS NOT NULL
            AND ((selected_alternative IS NOT NULL AND custom_resolution_json IS NULL)
                OR (selected_alternative IS NULL AND custom_resolution_json IS NOT NULL)))
    )
);

CREATE TABLE schema_draft_decision (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    sequence bigint NOT NULL,
    draft_revision bigint NOT NULL,
    type varchar(32) NOT NULL,
    review_state varchar(32) NOT NULL,
    candidate_identity text,
    prior_value_json text,
    resulting_value_json text,
    rationale text,
    created_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_decision_draft_fk
        FOREIGN KEY (draft_id) REFERENCES schema_draft (id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_decision_sequence_unique UNIQUE (draft_id, sequence),
    CONSTRAINT schema_draft_decision_sequence_check CHECK (sequence > 0 AND draft_revision >= 0),
    CONSTRAINT schema_draft_decision_type_check
        CHECK (type IN ('ACCEPT', 'REJECT', 'MODIFY', 'PIN', 'RESOLVE')),
    CONSTRAINT schema_draft_decision_review_state_check
        CHECK (review_state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'MODIFIED', 'PINNED'))
);

CREATE TABLE schema_draft_storage_mutation (
    id varchar(255) PRIMARY KEY,
    type varchar(32) NOT NULL,
    state varchar(32) NOT NULL,
    draft_id varchar(255),
    source_id varchar(255),
    content_uri text,
    retry_count integer NOT NULL DEFAULT 0,
    last_error text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_storage_mutation_draft_fk
        FOREIGN KEY (draft_id) REFERENCES schema_draft (id) ON DELETE SET NULL,
    CONSTRAINT schema_draft_storage_mutation_type_check CHECK (type IN ('STORE', 'DELETE')),
    CONSTRAINT schema_draft_storage_mutation_state_check
        CHECK (state IN ('PENDING', 'COMPLETED', 'COMPENSATED', 'FAILED')),
    CONSTRAINT schema_draft_storage_mutation_retry_check CHECK (retry_count >= 0),
    CONSTRAINT schema_draft_storage_mutation_terminal_check CHECK (
        (state = 'PENDING' AND completed_at IS NULL)
        OR (state IN ('COMPLETED', 'COMPENSATED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

ALTER TABLE schema_draft
    ADD CONSTRAINT schema_draft_running_run_fk
        FOREIGN KEY (running_analysis_run_id) REFERENCES schema_draft_analysis_run (id) ON DELETE SET NULL,
    ADD CONSTRAINT schema_draft_current_aggregate_fk
        FOREIGN KEY (current_aggregate_id) REFERENCES schema_draft_aggregate_revision (id) ON DELETE SET NULL;

ALTER TABLE schema_draft_analysis_run
    ADD CONSTRAINT schema_draft_analysis_run_aggregate_fk
        FOREIGN KEY (aggregate_revision_id) REFERENCES schema_draft_aggregate_revision (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX schema_draft_single_running_idx
    ON schema_draft (running_analysis_run_id) WHERE running_analysis_run_id IS NOT NULL;
CREATE INDEX schema_draft_kb_navigation_idx
    ON schema_draft (knowledge_base_id, updated_at DESC, id DESC);
CREATE INDEX schema_draft_status_idx ON schema_draft (status, updated_at);
CREATE INDEX schema_draft_source_navigation_idx
    ON schema_draft_source (draft_id, created_at, id);
CREATE INDEX schema_draft_source_active_idx
    ON schema_draft_source (draft_id, created_at, id) WHERE status = 'ACTIVE';
CREATE INDEX schema_draft_source_document_idx
    ON schema_draft_source (document_id) WHERE document_id IS NOT NULL;
CREATE INDEX schema_draft_source_revision_history_idx
    ON schema_draft_source_revision (source_id, revision DESC);
CREATE INDEX schema_draft_analysis_run_history_idx
    ON schema_draft_analysis_run (draft_id, created_at DESC, id DESC);
CREATE INDEX schema_draft_analysis_run_worker_idx
    ON schema_draft_analysis_run (status, claimed_at, created_at) WHERE status = 'RUNNING';
CREATE INDEX schema_draft_analysis_run_retry_idx
    ON schema_draft_analysis_run (retry_of_run_id) WHERE retry_of_run_id IS NOT NULL;
CREATE INDEX schema_draft_analysis_run_current_idx
    ON schema_draft_analysis_run (draft_id, current_result, created_at DESC);
CREATE INDEX schema_draft_source_result_history_idx
    ON schema_draft_source_result (run_id, created_at, id);
CREATE INDEX schema_draft_source_result_reuse_idx
    ON schema_draft_source_result (draft_id, reuse_key, completed_at DESC)
    WHERE status = 'SUCCEEDED';
CREATE INDEX schema_draft_aggregate_history_idx
    ON schema_draft_aggregate_revision (draft_id, revision DESC);
CREATE INDEX schema_draft_conflict_navigation_idx
    ON schema_draft_conflict (draft_id, aggregate_revision_id, coordinate, id);
CREATE INDEX schema_draft_conflict_history_idx
    ON schema_draft_conflict (draft_id, resolved, resolved_at DESC);
CREATE INDEX schema_draft_decision_history_idx
    ON schema_draft_decision (draft_id, sequence);
CREATE INDEX schema_draft_decision_candidate_idx
    ON schema_draft_decision (draft_id, candidate_identity, sequence DESC);
CREATE INDEX schema_draft_storage_mutation_pending_idx
    ON schema_draft_storage_mutation (state, created_at, id) WHERE state = 'PENDING';
CREATE INDEX schema_draft_storage_mutation_retention_idx
    ON schema_draft_storage_mutation (completed_at) WHERE state = 'COMPLETED';
