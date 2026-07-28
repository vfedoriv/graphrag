# schema-mutation Specification

## Purpose
Define update and delete behavior for persisted schema definitions.

## Requirements
### Requirement: Schema definitions are relationally owned
The system SHALL persist schema identity, content, state, and optimistic version in PostgreSQL and SHALL enforce unique `(name, version)` identity.

#### Scenario: A schema is saved
- **WHEN** a validated schema with a new name and version is saved
- **THEN** its assigned identity and original text content are committed relationally

#### Scenario: Duplicate identity is saved
- **WHEN** another schema uses an existing name and version
- **THEN** the operation is rejected without changing the existing schema

### Requirement: Existing schema mutation guards survive the store migration
The system MUST keep schema identity immutable, permit content replacement only for an inactive schema with unchanged identity, and prohibit update or deletion of an active schema.

#### Scenario: Inactive content is replaced
- **WHEN** replacement content retains the saved name and version and passes validation
- **THEN** the relational record is updated atomically

#### Scenario: Active schema mutation is attempted
- **WHEN** a caller updates or deletes an active schema
- **THEN** the operation is rejected and the relational record remains unchanged

### Requirement: Schema can be updated by id
The system SHALL allow an existing schema definition to be updated by replacing its persisted JSON content while preserving the schema record id and immutable `name + version` identity.

#### Scenario: Update inactive schema content
- **WHEN** a client sends valid replacement schema content to `PUT /api/v1/schemas/{schemaId}` for an existing schema that is not active for any knowledge base
- **THEN** the system persists the replacement content on the same schema id
- **AND** the system recalculates the schema content hash
- **AND** the response includes the updated schema details including content

#### Scenario: Update missing schema
- **WHEN** a client updates a schema id that does not exist
- **THEN** the system returns the existing not-found error behavior as `ProblemDetail`
- **AND** no schema record is created

#### Scenario: Update cannot change schema identity
- **WHEN** a client updates an existing schema with replacement content whose parsed `name` or `version` differs from the persisted schema
- **THEN** the system rejects the request as a conflict
- **AND** the persisted schema content and content hash are unchanged

#### Scenario: Update active schema is rejected
- **WHEN** a client updates a schema whose id is the active schema for any knowledge base
- **THEN** the system rejects the request as a conflict
- **AND** the persisted schema content and content hash are unchanged

### Requirement: Schema can be deleted by id
The system SHALL allow an existing schema definition to be deleted only when deletion does not leave knowledge-base schema references inconsistent.

#### Scenario: Delete inactive schema
- **WHEN** a client sends `DELETE /api/v1/schemas/{schemaId}` for an existing schema that is not active for any knowledge base
- **THEN** the system deletes the schema record
- **AND** the response has no body
- **AND** subsequent retrieval of that schema id returns the existing not-found error behavior

#### Scenario: Delete missing schema
- **WHEN** a client deletes a schema id that does not exist
- **THEN** the system returns the existing not-found error behavior as `ProblemDetail`
- **AND** no schema records are changed

#### Scenario: Delete inactive associated schema detaches relationships
- **WHEN** a client deletes a schema that is associated with a knowledge base but is not active for any knowledge base
- **THEN** the system removes relational ownership associations that point to the schema
- **AND** the system deletes the schema record
- **AND** knowledge base active schema references remain unchanged

#### Scenario: Delete active schema is rejected
- **WHEN** a client deletes a schema whose id is the active schema for any knowledge base
- **THEN** the system rejects the request as a conflict
- **AND** the knowledge base active schema remains unchanged

### Requirement: Schema mutation errors use established API conventions
The system MUST return schema mutation errors using the established RFC 7807 `ProblemDetail` error format.

#### Scenario: Invalid update payload returns problem detail
- **WHEN** a schema update request contains invalid request data
- **THEN** the system returns a validation error as `ProblemDetail`
- **AND** no partial schema update is persisted

#### Scenario: Conflict returns problem detail
- **WHEN** a schema update or delete request violates schema identity or active-schema rules
- **THEN** the system returns a conflict error as `ProblemDetail`
- **AND** the response does not include a partial success payload
