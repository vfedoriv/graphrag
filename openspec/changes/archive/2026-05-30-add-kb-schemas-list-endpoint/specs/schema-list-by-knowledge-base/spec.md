## ADDED Requirements

### Requirement: List schemas by knowledge base
The system SHALL provide a `GET` API operation that returns schemas associated with a specific knowledge base identifier.

#### Scenario: Return schemas associated with knowledge base
- **WHEN** a client calls the endpoint with a valid knowledge base identifier that has associated schemas
- **THEN** the system returns `200 OK`
- **AND** the response body contains a list of schemas associated only with that knowledge base

#### Scenario: Return empty list when no schemas are associated
- **WHEN** a client calls the endpoint with a valid knowledge base identifier that has no associated schemas
- **THEN** the system returns `200 OK`
- **AND** the response body contains an empty list

### Requirement: Preserve response and error conventions
The system MUST return schema list items using the established schema response contract and MUST use RFC 7807 `ProblemDetail` for errors.

#### Scenario: Unknown knowledge base returns not found problem
- **WHEN** a client calls the endpoint with a knowledge base identifier that does not exist
- **THEN** the system returns the existing not-found error behavior as `ProblemDetail`
- **AND** the response does not include a partial list payload
