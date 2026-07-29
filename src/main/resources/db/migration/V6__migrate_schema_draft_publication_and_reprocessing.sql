CREATE TABLE schema_draft_evaluation_run (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    knowledge_base_id varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    draft_revision bigint NOT NULL,
    aggregate_revision_id varchar(255) NOT NULL,
    projection_json text NOT NULL,
    projection_content_hash varchar(64) NOT NULL,
    guidance_json text NOT NULL,
    decisions_json text NOT NULL,
    decisions_fingerprint varchar(64) NOT NULL,
    document_snapshot_json text NOT NULL,
    ai_profile_id varchar(255) NOT NULL,
    ai_profile_revision bigint NOT NULL,
    prompt_revision varchar(255) NOT NULL,
    contract_revision varchar(255) NOT NULL,
    settings_json text NOT NULL,
    snapshot_fingerprint varchar(64) NOT NULL,
    retry_of_run_id varchar(255),
    retry_count integer NOT NULL DEFAULT 0,
    claimed_by varchar(255),
    claimed_at timestamptz,
    claim_until timestamptz,
    total_documents integer NOT NULL DEFAULT 0,
    succeeded_documents integer NOT NULL DEFAULT 0,
    failed_documents integer NOT NULL DEFAULT 0,
    stale_documents integer NOT NULL DEFAULT 0,
    metrics_json text,
    advisory_json text,
    failure_category varchar(255),
    retryable boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_evaluation_run_owner_fk
        FOREIGN KEY (draft_id, knowledge_base_id)
        REFERENCES schema_draft (id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_evaluation_run_aggregate_fk
        FOREIGN KEY (aggregate_revision_id, draft_id)
        REFERENCES schema_draft_aggregate_revision (id, draft_id),
    CONSTRAINT schema_draft_evaluation_run_ai_profile_fk
        FOREIGN KEY (ai_profile_id) REFERENCES ai_profile (id),
    CONSTRAINT schema_draft_evaluation_run_retry_fk
        FOREIGN KEY (retry_of_run_id) REFERENCES schema_draft_evaluation_run (id) ON DELETE SET NULL,
    CONSTRAINT schema_draft_evaluation_run_id_owner_unique UNIQUE (id, draft_id),
    CONSTRAINT schema_draft_evaluation_run_identity_unique
        UNIQUE (draft_id, draft_revision, aggregate_revision_id, snapshot_fingerprint),
    CONSTRAINT schema_draft_evaluation_run_status_check
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT schema_draft_evaluation_run_counts_check CHECK (
        draft_revision >= 0 AND ai_profile_revision >= 0 AND retry_count >= 0
        AND total_documents >= 0 AND succeeded_documents >= 0
        AND failed_documents >= 0 AND stale_documents >= 0
        AND succeeded_documents + failed_documents + stale_documents <= total_documents
    ),
    CONSTRAINT schema_draft_evaluation_run_claim_check CHECK (
        (claimed_by IS NULL AND claimed_at IS NULL AND claim_until IS NULL)
        OR (claimed_by IS NOT NULL AND claimed_at IS NOT NULL AND claim_until IS NOT NULL)
    ),
    CONSTRAINT schema_draft_evaluation_run_terminal_check CHECK (
        (status = 'QUEUED' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN ('COMPLETED', 'PARTIAL', 'FAILED', 'INTERRUPTED') AND completed_at IS NOT NULL)
    )
);

CREATE TABLE schema_draft_evaluation_outcome (
    id varchar(255) PRIMARY KEY,
    run_id varchar(255) NOT NULL,
    draft_id varchar(255) NOT NULL,
    document_id varchar(255) NOT NULL,
    document_sha256 varchar(64) NOT NULL,
    reuse_key varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    reused boolean NOT NULL DEFAULT false,
    reused_from_outcome_id varchar(255),
    chunk_count integer NOT NULL DEFAULT 0,
    metrics_json text,
    evidence_coordinates_json text,
    failure_category varchar(255),
    retryable boolean NOT NULL DEFAULT false,
    started_at timestamptz,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_evaluation_outcome_run_fk
        FOREIGN KEY (run_id, draft_id)
        REFERENCES schema_draft_evaluation_run (id, draft_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_evaluation_outcome_document_fk
        FOREIGN KEY (document_id) REFERENCES document_upload (id),
    CONSTRAINT schema_draft_evaluation_outcome_reused_fk
        FOREIGN KEY (reused_from_outcome_id) REFERENCES schema_draft_evaluation_outcome (id),
    CONSTRAINT schema_draft_evaluation_outcome_run_document_unique
        UNIQUE (run_id, document_id),
    CONSTRAINT schema_draft_evaluation_outcome_run_reuse_unique
        UNIQUE (run_id, reuse_key),
    CONSTRAINT schema_draft_evaluation_outcome_status_check CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'REUSED', 'FAILED', 'STALE_SOURCE', 'INTERRUPTED')
    ),
    CONSTRAINT schema_draft_evaluation_outcome_shape_check CHECK (
        chunk_count >= 0
        AND ((status = 'QUEUED' AND started_at IS NULL AND completed_at IS NULL)
            OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
            OR (status IN ('SUCCEEDED', 'REUSED', 'FAILED', 'STALE_SOURCE', 'INTERRUPTED')
                AND completed_at IS NOT NULL))
        AND (NOT reused OR (status = 'REUSED' AND reused_from_outcome_id IS NOT NULL))
    )
);

CREATE TABLE schema_draft_publication (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    knowledge_base_id varchar(255) NOT NULL,
    target_identity varchar(512) NOT NULL,
    draft_revision bigint NOT NULL,
    aggregate_revision_id varchar(255) NOT NULL,
    projection_content_hash varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    schema_id varchar(255),
    retry_count integer NOT NULL DEFAULT 0,
    failure_category varchar(255),
    retryable boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_draft_publication_owner_fk
        FOREIGN KEY (draft_id, knowledge_base_id)
        REFERENCES schema_draft (id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT schema_draft_publication_aggregate_fk
        FOREIGN KEY (aggregate_revision_id, draft_id)
        REFERENCES schema_draft_aggregate_revision (id, draft_id),
    CONSTRAINT schema_draft_publication_schema_fk
        FOREIGN KEY (schema_id) REFERENCES schema_definition (id),
    CONSTRAINT schema_draft_publication_draft_unique UNIQUE (draft_id),
    CONSTRAINT schema_draft_publication_target_unique UNIQUE (target_identity),
    CONSTRAINT schema_draft_publication_revision_identity_unique
        UNIQUE (draft_id, draft_revision, aggregate_revision_id, projection_content_hash),
    CONSTRAINT schema_draft_publication_status_check
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT schema_draft_publication_shape_check CHECK (
        draft_revision >= 0 AND retry_count >= 0
        AND ((status = 'PENDING' AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND schema_id IS NOT NULL AND completed_at IS NOT NULL)
            OR (status IN ('FAILED', 'INTERRUPTED') AND completed_at IS NOT NULL))
    )
);

CREATE TABLE schema_reprocessing_plan (
    id varchar(255) PRIMARY KEY,
    draft_id varchar(255) NOT NULL,
    knowledge_base_id varchar(255) NOT NULL,
    schema_id varchar(255) NOT NULL,
    schema_content_hash varchar(64) NOT NULL,
    ai_profile_id varchar(255) NOT NULL,
    ai_profile_revision bigint NOT NULL,
    processing_options_json text NOT NULL,
    retry_of_plan_id varchar(255),
    retry_count integer NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL,
    claimed_by varchar(255),
    claimed_at timestamptz,
    claim_until timestamptz,
    total_documents integer NOT NULL DEFAULT 0,
    queued_documents integer NOT NULL DEFAULT 0,
    running_documents integer NOT NULL DEFAULT 0,
    succeeded_documents integer NOT NULL DEFAULT 0,
    failed_documents integer NOT NULL DEFAULT 0,
    stale_documents integer NOT NULL DEFAULT 0,
    blocked_documents integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_reprocessing_plan_owner_fk
        FOREIGN KEY (draft_id, knowledge_base_id)
        REFERENCES schema_draft (id, knowledge_base_id) ON DELETE CASCADE,
    CONSTRAINT schema_reprocessing_plan_schema_fk
        FOREIGN KEY (schema_id) REFERENCES schema_definition (id),
    CONSTRAINT schema_reprocessing_plan_ai_profile_fk
        FOREIGN KEY (ai_profile_id) REFERENCES ai_profile (id),
    CONSTRAINT schema_reprocessing_plan_retry_fk
        FOREIGN KEY (retry_of_plan_id) REFERENCES schema_reprocessing_plan (id) ON DELETE SET NULL,
    CONSTRAINT schema_reprocessing_plan_id_owner_unique UNIQUE (id, knowledge_base_id),
    CONSTRAINT schema_reprocessing_plan_status_check
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED', 'INTERRUPTED')),
    CONSTRAINT schema_reprocessing_plan_counts_check CHECK (
        ai_profile_revision >= 0 AND retry_count >= 0 AND total_documents >= 0
        AND queued_documents >= 0 AND running_documents >= 0 AND succeeded_documents >= 0
        AND failed_documents >= 0 AND stale_documents >= 0 AND blocked_documents >= 0
        AND queued_documents + running_documents + succeeded_documents + failed_documents
            + stale_documents + blocked_documents = total_documents
    ),
    CONSTRAINT schema_reprocessing_plan_claim_check CHECK (
        (claimed_by IS NULL AND claimed_at IS NULL AND claim_until IS NULL)
        OR (claimed_by IS NOT NULL AND claimed_at IS NOT NULL AND claim_until IS NOT NULL)
    ),
    CONSTRAINT schema_reprocessing_plan_terminal_check CHECK (
        (status = 'QUEUED' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN ('COMPLETED', 'PARTIAL', 'FAILED', 'INTERRUPTED') AND completed_at IS NOT NULL)
    )
);

CREATE TABLE schema_reprocessing_item (
    id varchar(255) PRIMARY KEY,
    plan_id varchar(255) NOT NULL,
    document_id varchar(255) NOT NULL,
    document_sha256 varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    failure_category varchar(255),
    retryable boolean NOT NULL DEFAULT true,
    retry_count integer NOT NULL DEFAULT 0,
    prior_item_id varchar(255),
    claimed_by varchar(255),
    claimed_at timestamptz,
    claim_until timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_reprocessing_item_plan_fk
        FOREIGN KEY (plan_id) REFERENCES schema_reprocessing_plan (id) ON DELETE CASCADE,
    CONSTRAINT schema_reprocessing_item_document_fk
        FOREIGN KEY (document_id) REFERENCES document_upload (id),
    CONSTRAINT schema_reprocessing_item_prior_fk
        FOREIGN KEY (prior_item_id) REFERENCES schema_reprocessing_item (id) ON DELETE SET NULL,
    CONSTRAINT schema_reprocessing_item_plan_document_unique UNIQUE (plan_id, document_id),
    CONSTRAINT schema_reprocessing_item_status_check CHECK (
        status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'STALE_SOURCE',
            'BLOCKED', 'INTERRUPTED', 'SKIPPED')
    ),
    CONSTRAINT schema_reprocessing_item_retry_check CHECK (retry_count >= 0),
    CONSTRAINT schema_reprocessing_item_claim_check CHECK (
        (claimed_by IS NULL AND claimed_at IS NULL AND claim_until IS NULL)
        OR (claimed_by IS NOT NULL AND claimed_at IS NOT NULL AND claim_until IS NOT NULL)
    ),
    CONSTRAINT schema_reprocessing_item_terminal_check CHECK (
        (status = 'QUEUED' AND started_at IS NULL AND completed_at IS NULL)
        OR (status = 'RUNNING' AND started_at IS NOT NULL AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED', 'STALE_SOURCE', 'BLOCKED', 'INTERRUPTED', 'SKIPPED')
            AND completed_at IS NOT NULL)
    )
);

CREATE INDEX schema_draft_evaluation_run_history_idx
    ON schema_draft_evaluation_run (draft_id, created_at DESC, id DESC);
CREATE INDEX schema_draft_evaluation_run_worker_idx
    ON schema_draft_evaluation_run (status, claim_until, created_at, id)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX schema_draft_evaluation_run_retry_idx
    ON schema_draft_evaluation_run (retryable, retry_count, completed_at)
    WHERE retryable;
CREATE INDEX schema_draft_evaluation_outcome_history_idx
    ON schema_draft_evaluation_outcome (run_id, document_id, id);
CREATE INDEX schema_draft_evaluation_outcome_reuse_idx
    ON schema_draft_evaluation_outcome (draft_id, reuse_key, completed_at DESC, id DESC)
    WHERE status IN ('SUCCEEDED', 'REUSED');

CREATE INDEX schema_draft_publication_recovery_idx
    ON schema_draft_publication (status, retryable, created_at, id)
    WHERE status IN ('PENDING', 'FAILED', 'INTERRUPTED');
CREATE INDEX schema_draft_publication_schema_idx
    ON schema_draft_publication (schema_id) WHERE schema_id IS NOT NULL;

CREATE INDEX schema_reprocessing_plan_navigation_idx
    ON schema_reprocessing_plan (knowledge_base_id, created_at DESC, id DESC);
CREATE INDEX schema_reprocessing_plan_draft_idx
    ON schema_reprocessing_plan (knowledge_base_id, draft_id, created_at DESC, id DESC);
CREATE INDEX schema_reprocessing_plan_worker_idx
    ON schema_reprocessing_plan (status, claim_until, created_at, id)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX schema_reprocessing_plan_retry_idx
    ON schema_reprocessing_plan (retry_of_plan_id) WHERE retry_of_plan_id IS NOT NULL;
CREATE INDEX schema_reprocessing_item_navigation_idx
    ON schema_reprocessing_item (plan_id, document_id, id);
CREATE INDEX schema_reprocessing_item_worker_idx
    ON schema_reprocessing_item (status, retryable, claim_until, plan_id, id)
    WHERE status IN ('QUEUED', 'RUNNING', 'FAILED', 'INTERRUPTED');
CREATE INDEX schema_reprocessing_item_retry_idx
    ON schema_reprocessing_item (prior_item_id) WHERE prior_item_id IS NOT NULL;
