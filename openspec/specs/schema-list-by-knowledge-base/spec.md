# schema-list-by-knowledge-base Specification

## Purpose
TBD - created by archiving change add-kb-schemas-list-endpoint. Update Purpose after archive.
## Requirements
### Requirement: Associate created schema with knowledge base
The system SHALL allow clients to create a schema and associate it with an existing knowledge base without activating the schema.

#### Scenario: Create schema associated with knowledge base
- **WHEN** a client creates a valid schema with a knowledge base identifier
- **THEN** the system persists the schema definition
- **AND** the system creates a relational ownership association from that knowledge base to the schema
- **AND** the schema remains inactive unless separately activated
- **AND** the associated schema is returned by the knowledge-base schema list

#### Scenario: Create schema without knowledge base remains global
- **WHEN** a client creates a valid schema without a knowledge base identifier
- **THEN** the system persists the schema definition
- **AND** the system does not create a knowledge-base association
- **AND** existing global schema creation behavior is preserved

#### Scenario: Create schema for unknown knowledge base is rejected
- **WHEN** a client creates a schema with a knowledge base identifier that does not exist
- **THEN** the system rejects the request using `ProblemDetail`
- **AND** no schema definition or knowledge-base association is persisted

### Requirement: Attach existing schema to knowledge base
The system SHALL allow clients to associate an existing schema with an existing knowledge base without activating the schema.

#### Scenario: Attach existing schema
- **WHEN** a client attaches an existing schema to an existing knowledge base
- **THEN** the system creates a relational ownership association from that knowledge base to the schema
- **AND** the knowledge base active schema remains unchanged
- **AND** the schema status remains unchanged unless it was already active for that knowledge base
- **AND** the attached schema is returned by the knowledge-base schema list

#### Scenario: Attach existing schema is idempotent
- **WHEN** a client attaches a schema that is already associated with the knowledge base
- **THEN** the request succeeds
- **AND** the system stores only one relational ownership association from that knowledge base to that schema
- **AND** the knowledge base active schema remains unchanged

#### Scenario: Attach unknown schema is rejected
- **WHEN** a client attaches a schema identifier that does not exist
- **THEN** the system rejects the request using `ProblemDetail`
- **AND** no knowledge-base association is persisted

#### Scenario: Attach schema to unknown knowledge base is rejected
- **WHEN** a client attaches a schema to a knowledge base identifier that does not exist
- **THEN** the system rejects the request using `ProblemDetail`
- **AND** no knowledge-base association is persisted

### Requirement: List schemas by knowledge base
The system SHALL provide a `GET` API operation that returns schemas associated with a specific knowledge base identifier, including schemas associated by creation, attachment, or activation.

#### Scenario: Return schemas associated with knowledge base
- **WHEN** a client calls the endpoint with a valid knowledge base identifier that has associated schemas
- **THEN** the system returns `200 OK`
- **AND** the response body contains a list of schemas associated only with that knowledge base

#### Scenario: Return newly created associated schema
- **WHEN** a schema was created with a valid knowledge base identifier
- **AND** a client calls the schema list endpoint for that knowledge base
- **THEN** the system returns `200 OK`
- **AND** the response body contains the newly created schema
- **AND** the schema does not need to be active to appear in the list

#### Scenario: Return attached schema
- **WHEN** an existing schema was attached to a valid knowledge base
- **AND** a client calls the schema list endpoint for that knowledge base
- **THEN** the system returns `200 OK`
- **AND** the response body contains the attached schema
- **AND** the active schema for the knowledge base is unchanged

#### Scenario: Return empty list when no schemas are associated
- **WHEN** a client calls the endpoint with a valid knowledge base identifier that has no associated schemas
- **THEN** the system returns `200 OK`
- **AND** the response body contains an empty list

### Requirement: Knowledge-base schema listings use relational ownership
The system SHALL list only schema definitions associated with the requested knowledge base through relational ownership and preserve existing sorting, pagination, content inclusion, and not-found behavior.

#### Scenario: Associated schemas are listed
- **WHEN** a caller lists schemas for an existing knowledge base
- **THEN** only its relationally associated schemas are returned
- **AND** active-state projection remains correct

### Requirement: Preserve response and error conventions
The system MUST return schema list items using the established schema response contract and MUST use RFC 7807 `ProblemDetail` for errors.

#### Scenario: Unknown knowledge base returns not found problem
- **WHEN** a client calls the endpoint with a knowledge base identifier that does not exist
- **THEN** the system returns the existing not-found error behavior as `ProblemDetail`
- **AND** the response does not include a partial list payload
