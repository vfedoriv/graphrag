## ADDED Requirements

### Requirement: Schema lifecycle operations accept JSON definitions only
The system SHALL accept schema definition content only when the content is valid JSON for schema lifecycle operations.

#### Scenario: Schema creation accepts JSON content
- **WHEN** a client posts to `/api/v1/schemas` with valid JSON schema definition content
- **THEN** the system validates and persists the schema
- **AND** the persisted format is `SchemaFormat.JSON`

#### Scenario: Schema validation accepts JSON content
- **WHEN** a client posts to `/api/v1/schemas/validate` with valid JSON schema definition content
- **THEN** the system returns successful validation

#### Scenario: YAML content is rejected
- **WHEN** a client posts YAML schema definition content to schema creation or validation endpoints
- **THEN** the system rejects the request as invalid
- **AND** the error indicates JSON is required

### Requirement: Bootstrap loading uses JSON schema resources
The system SHALL load bootstrap schema resources from JSON files.

#### Scenario: Bootstrap schemas load from JSON resources
- **WHEN** application startup runs schema bootstrap loading
- **THEN** the system loads resources matching `classpath:/schemas/*.json`
- **AND** parsed schema definitions are validated as JSON before persistence

### Requirement: Migration requires legacy YAML schema data cleanup
The system SHALL require legacy persisted YAML schema data to be removed before running the JSON-only migrated runtime.

#### Scenario: Upgrade precondition documents manual cleanup
- **WHEN** operators prepare to deploy the JSON-only migration
- **THEN** they are instructed to remove previously persisted YAML schema records from Neo4j before startup
- **AND** no runtime compatibility for YAML format is required after upgrade
