## ADDED Requirements

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
