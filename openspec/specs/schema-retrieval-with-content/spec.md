# schema-retrieval-with-content Specification

## Purpose
TBD - created by archiving change include-schema-content-in-get-schema. Update Purpose after archive.
## Requirements
### Requirement: Single-schema retrieval returns persisted schema content
The system SHALL include the persisted schema content in responses from `GET /api/v1/schemas/{schemaId}` when the schema exists.

#### Scenario: Retrieve schema by id includes content
- **WHEN** a client calls `GET /api/v1/schemas/{schemaId}` for an existing schema
- **THEN** the response includes schema metadata fields already returned today
- **AND** the response includes a `content` field containing the persisted schema definition

#### Scenario: Returned content matches persisted definition
- **WHEN** a schema is persisted with a specific schema definition text
- **THEN** `GET /api/v1/schemas/{schemaId}` returns that same definition text in the `content` field

#### Scenario: Schema not found behavior is unchanged
- **WHEN** a client calls `GET /api/v1/schemas/{schemaId}` for a missing schema id
- **THEN** the system returns the existing not-found error behavior
- **AND** no partial success payload is returned

