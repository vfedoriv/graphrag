CREATE TABLE runtime_setting_override (
    setting_key varchar(255) PRIMARY KEY,
    setting_value text NOT NULL,
    lifecycle_state varchar(32) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT runtime_setting_override_lifecycle_check
        CHECK (lifecycle_state IN ('active', 'pending-restart'))
);

CREATE TABLE ai_profile (
    id varchar(255) PRIMARY KEY,
    name varchar(255) NOT NULL,
    base_url text NOT NULL,
    api_key text,
    chat_model varchar(255) NOT NULL,
    embedding_model varchar(255) NOT NULL,
    embedding_dimensions integer NOT NULL,
    timeout_seconds integer NOT NULL,
    max_retries integer NOT NULL,
    default_profile boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ai_profile_embedding_dimensions_check CHECK (embedding_dimensions > 0),
    CONSTRAINT ai_profile_timeout_seconds_check CHECK (timeout_seconds > 0),
    CONSTRAINT ai_profile_max_retries_check CHECK (max_retries >= 0),
    CONSTRAINT ai_profile_revision_check CHECK (revision > 0)
);

CREATE UNIQUE INDEX ai_profile_single_default_idx
    ON ai_profile (default_profile)
    WHERE default_profile;
