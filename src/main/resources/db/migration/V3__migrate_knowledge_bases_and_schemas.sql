CREATE TABLE knowledge_base (
    id varchar(255) PRIMARY KEY,
    name varchar(255) NOT NULL,
    active_ai_profile_id varchar(255) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT knowledge_base_ai_profile_fk
        FOREIGN KEY (active_ai_profile_id) REFERENCES ai_profile (id)
);

CREATE INDEX knowledge_base_active_ai_profile_idx
    ON knowledge_base (active_ai_profile_id);

CREATE TABLE schema_definition (
    id varchar(255) PRIMARY KEY,
    name varchar(255) NOT NULL,
    schema_version integer NOT NULL,
    source_type varchar(32) NOT NULL,
    format varchar(32) NOT NULL,
    content text NOT NULL,
    content_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT schema_definition_identity_unique UNIQUE (name, schema_version),
    CONSTRAINT schema_definition_version_check CHECK (schema_version > 0),
    CONSTRAINT schema_definition_source_type_check
        CHECK (source_type IN ('PREDEFINED', 'GENERATED')),
    CONSTRAINT schema_definition_format_check CHECK (format IN ('JSON'))
);

CREATE TABLE knowledge_base_schema (
    knowledge_base_id varchar(255) NOT NULL,
    schema_id varchar(255) NOT NULL,
    active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (knowledge_base_id, schema_id),
    CONSTRAINT knowledge_base_schema_kb_fk
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id) ON DELETE CASCADE,
    CONSTRAINT knowledge_base_schema_schema_fk
        FOREIGN KEY (schema_id) REFERENCES schema_definition (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX knowledge_base_schema_single_active_idx
    ON knowledge_base_schema (knowledge_base_id)
    WHERE active;

CREATE INDEX knowledge_base_schema_schema_idx
    ON knowledge_base_schema (schema_id);
