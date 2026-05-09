## ADDED Requirements

### Requirement: Generate schema YAML from text with a domain example
The system SHALL generate graph schema YAML from unstructured text only when the request includes schema metadata, source text, and a non-blank domain example containing representative entities and relationships for the source domain.

#### Scenario: Text schema generation succeeds with example
- **WHEN** a client posts to `/api/v1/schemas/generate` with `name`, `version`, `description`, `text`, and `example`
- **THEN** the system returns generated YAML for the inferred graph schema
- **AND** the graph extraction step uses the provided `example` as domain guidance

#### Scenario: Text schema generation rejects missing example
- **WHEN** a client posts to `/api/v1/schemas/generate` without `example` or with a blank `example`
- **THEN** the system rejects the request as invalid
- **AND** no LLM schema generation is invoked

### Requirement: Generate schema YAML from file with a domain example
The system SHALL generate graph schema YAML from an uploaded file only when the multipart request includes schema metadata, a supported file, and a non-blank domain example containing representative entities and relationships for the file domain.

#### Scenario: File schema generation succeeds with example
- **WHEN** a client posts to `/api/v1/schemas/generate/from-file` with `name`, `version`, optional `description`, `example`, and `file`
- **THEN** the system parses the file, generates YAML for the inferred graph schema, and returns the YAML
- **AND** the graph extraction step uses the provided `example` as domain guidance

#### Scenario: File schema generation rejects missing example
- **WHEN** a client posts to `/api/v1/schemas/generate/from-file` without `example` or with a blank `example`
- **THEN** the system rejects the request as invalid
- **AND** no LLM schema generation is invoked

### Requirement: Generated schema endpoints do not persist schemas
The system SHALL NOT save generated schema YAML from schema generation endpoints directly to the schema registry.

#### Scenario: Generated schema is returned for review only
- **WHEN** a client successfully generates schema YAML from text or file input
- **THEN** the response contains the generated YAML
- **AND** no generated schema record is created in the schema registry
- **AND** the response does not identify a saved schema version

#### Scenario: Save parameter is not accepted
- **WHEN** a client sends a `save` field or `save` multipart parameter to a schema generation endpoint
- **THEN** the system does not use it to persist the generated YAML
- **AND** clients must use the schema creation endpoint to save reviewed YAML
