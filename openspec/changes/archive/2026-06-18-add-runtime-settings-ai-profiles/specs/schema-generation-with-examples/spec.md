## ADDED Requirements

### Requirement: Knowledge-base-scoped schema generation uses active AI profile
The system SHALL expose knowledge-base-scoped schema generation endpoints that use the target knowledge base active AI profile for model calls.

#### Scenario: Knowledge-base text schema generation succeeds with example
- **WHEN** a client posts to `/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/generate` with `name`, `version`, `description`, `text`, and `example`
- **THEN** the system returns generated JSON for the inferred graph schema
- **AND** the model call uses the active AI profile assigned to that knowledge base
- **AND** no generated schema record is created in the schema registry

#### Scenario: Knowledge-base file schema generation succeeds with example
- **WHEN** a client posts to `/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/generate/from-file` with `name`, `version`, optional `description`, `example`, and `file`
- **THEN** the system parses the file, generates JSON for the inferred graph schema, and returns the JSON
- **AND** the model call uses the active AI profile assigned to that knowledge base
- **AND** no generated schema record is created in the schema registry

#### Scenario: Knowledge-base schema generation has no active profile
- **WHEN** a client invokes a knowledge-base-scoped schema generation endpoint and the knowledge base has no resolvable active AI profile
- **THEN** the system rejects the request with a clear configuration error

### Requirement: Knowledge-base-scoped schema example generation uses active AI profile
The system SHALL expose knowledge-base-scoped schema example generation endpoints that use the target knowledge base active AI profile for model calls.

#### Scenario: Knowledge-base text example generation succeeds
- **WHEN** a client posts to `/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/generate/example` with valid text and optional user guidance
- **THEN** the system returns a generated schema example
- **AND** the model call uses the active AI profile assigned to that knowledge base

#### Scenario: Knowledge-base file example generation succeeds
- **WHEN** a client posts to `/api/v1/knowledge-bases/{knowledgeBaseId}/schemas/generate/example/from-file` with a supported file and optional user guidance
- **THEN** the system parses the file and returns a generated schema example
- **AND** the model call uses the active AI profile assigned to that knowledge base

### Requirement: Existing global schema generation endpoints remain compatible
The system SHALL preserve existing global schema generation endpoint behavior while knowledge-base-scoped alternatives are added.

#### Scenario: Existing global text schema generation is called
- **WHEN** a client posts to `/api/v1/schemas/generate` with the existing valid request shape
- **THEN** the endpoint continues to return generated schema JSON
- **AND** clients are not required to provide a knowledge base identifier

#### Scenario: Existing global schema example generation is called
- **WHEN** a client posts to `/api/v1/schemas/generate/example` with the existing valid request shape
- **THEN** the endpoint continues to return a generated schema example
- **AND** clients are not required to provide a knowledge base identifier
