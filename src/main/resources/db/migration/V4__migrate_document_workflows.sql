CREATE TABLE document_upload (
    id varchar(255) PRIMARY KEY,
    knowledge_base_id varchar(255) NOT NULL,
    original_filename varchar(1024),
    content_type varchar(255),
    size_bytes bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    content_uri text,
    status varchar(32) NOT NULL,
    uploaded_at timestamptz NOT NULL,
    processed_at timestamptz,
    error_message text,
    processing_defaults_json text,
    processing_defaults_updated_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT document_upload_kb_fk FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id),
    CONSTRAINT document_upload_digest_unique UNIQUE (knowledge_base_id, sha256),
    CONSTRAINT document_upload_size_check CHECK (size_bytes >= 0),
    CONSTRAINT document_upload_sha256_check CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT document_upload_status_check
        CHECK (status IN ('UPLOADED', 'PARSING', 'EMBEDDING', 'EXTRACTING_GRAPH', 'COMPLETED', 'FAILED'))
);

CREATE INDEX document_upload_kb_created_idx
    ON document_upload (knowledge_base_id, uploaded_at DESC, id DESC);
CREATE INDEX document_upload_status_recovery_idx
    ON document_upload (status, uploaded_at)
    WHERE status IN ('PARSING', 'EMBEDDING', 'EXTRACTING_GRAPH');

CREATE TABLE document_processing_run (
    id varchar(255) PRIMARY KEY,
    document_id varchar(255) NOT NULL,
    knowledge_base_id varchar(255) NOT NULL,
    source_sha256 varchar(64) NOT NULL,
    parser_id varchar(255) NOT NULL,
    file_format varchar(255) NOT NULL,
    requested_options_json text NOT NULL,
    saved_defaults_json text NOT NULL,
    effective_options_json text NOT NULL,
    status varchar(32) NOT NULL,
    stage varchar(64) NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    error_message text,
    active_completed boolean NOT NULL DEFAULT false,
    retry_count integer NOT NULL DEFAULT 0,
    retry_of_run_id varchar(255),
    claimed_by varchar(255),
    claim_until timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT document_processing_run_document_fk
        FOREIGN KEY (document_id) REFERENCES document_upload (id) ON DELETE CASCADE,
    CONSTRAINT document_processing_run_kb_fk FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id),
    CONSTRAINT document_processing_run_retry_fk
        FOREIGN KEY (retry_of_run_id) REFERENCES document_processing_run (id) ON DELETE SET NULL,
    CONSTRAINT document_processing_run_status_check CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT document_processing_run_retry_count_check CHECK (retry_count >= 0),
    CONSTRAINT document_processing_run_terminal_check CHECK (
        (status = 'RUNNING' AND completed_at IS NULL)
        OR (status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
    ),
    CONSTRAINT document_processing_run_active_completion_check
        CHECK (NOT active_completed OR (status = 'COMPLETED' AND completed_at IS NOT NULL))
);

CREATE UNIQUE INDEX document_processing_run_single_active_completed_idx
    ON document_processing_run (document_id) WHERE active_completed;
CREATE INDEX document_processing_run_history_idx
    ON document_processing_run (document_id, started_at, id);
CREATE INDEX document_processing_run_recovery_idx
    ON document_processing_run (status, started_at) WHERE status = 'RUNNING';
CREATE INDEX document_processing_run_claim_idx
    ON document_processing_run (claim_until, started_at) WHERE status = 'RUNNING';
CREATE INDEX document_processing_run_retry_idx
    ON document_processing_run (retry_of_run_id) WHERE retry_of_run_id IS NOT NULL;

CREATE TABLE extraction_run (
    id varchar(255) PRIMARY KEY,
    document_id varchar(255) NOT NULL,
    schema_id varchar(255) NOT NULL,
    model varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    error_message text,
    retry_count integer NOT NULL DEFAULT 0,
    retry_of_run_id varchar(255),
    claimed_by varchar(255),
    claim_until timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT extraction_run_document_fk
        FOREIGN KEY (document_id) REFERENCES document_upload (id) ON DELETE CASCADE,
    CONSTRAINT extraction_run_schema_fk FOREIGN KEY (schema_id) REFERENCES schema_definition (id),
    CONSTRAINT extraction_run_retry_fk
        FOREIGN KEY (retry_of_run_id) REFERENCES extraction_run (id) ON DELETE SET NULL,
    CONSTRAINT extraction_run_status_check CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT extraction_run_retry_count_check CHECK (retry_count >= 0),
    CONSTRAINT extraction_run_terminal_check CHECK (
        (status = 'RUNNING' AND completed_at IS NULL)
        OR (status IN ('COMPLETED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE INDEX extraction_run_history_idx ON extraction_run (document_id, started_at, id);
CREATE INDEX extraction_run_status_history_idx ON extraction_run (document_id, status, started_at, id);
CREATE INDEX extraction_run_recovery_idx ON extraction_run (status, started_at) WHERE status = 'RUNNING';
CREATE INDEX extraction_run_claim_idx ON extraction_run (claim_until, started_at) WHERE status = 'RUNNING';
CREATE INDEX extraction_run_retry_idx ON extraction_run (retry_of_run_id) WHERE retry_of_run_id IS NOT NULL;

CREATE TABLE document_storage_mutation (
    id varchar(255) PRIMARY KEY,
    type varchar(32) NOT NULL,
    state varchar(32) NOT NULL,
    knowledge_base_id varchar(255) NOT NULL,
    document_id varchar(255) NOT NULL,
    content_uri text,
    previous_content_uri text,
    retry_count integer NOT NULL DEFAULT 0,
    last_error text,
    created_at timestamptz NOT NULL,
    completed_at timestamptz,
    updated_at timestamptz NOT NULL,
    claimed_by varchar(255),
    claim_until timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT document_storage_mutation_kb_fk FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id),
    CONSTRAINT document_storage_mutation_type_check
        CHECK (type IN ('STORE', 'DELETE', 'DELETE_REPLACED_CONTENT')),
    CONSTRAINT document_storage_mutation_state_check
        CHECK (state IN ('PENDING', 'COMPLETED', 'COMPENSATED', 'FAILED')),
    CONSTRAINT document_storage_mutation_retry_count_check CHECK (retry_count >= 0),
    CONSTRAINT document_storage_mutation_terminal_check CHECK (
        (state = 'PENDING' AND completed_at IS NULL)
        OR (state IN ('COMPLETED', 'COMPENSATED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE INDEX document_storage_mutation_pending_idx
    ON document_storage_mutation (state, created_at, id) WHERE state = 'PENDING';
CREATE INDEX document_storage_mutation_claim_idx
    ON document_storage_mutation (claim_until, created_at, id) WHERE state = 'PENDING';
CREATE INDEX document_storage_mutation_document_idx
    ON document_storage_mutation (document_id, created_at);
CREATE INDEX document_storage_mutation_retention_idx
    ON document_storage_mutation (completed_at) WHERE state IN ('COMPLETED', 'COMPENSATED');
