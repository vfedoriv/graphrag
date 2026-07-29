## ADDED Requirements

### Requirement: Publication is revision-specific relational state
The system SHALL persist publication readiness, exact aggregate revision identity, resulting schema identity, lifecycle state, retries, and optimistic version in PostgreSQL.

#### Scenario: A ready revision is published
- **WHEN** the selected aggregate satisfies currentness, review, and evaluation readiness
- **THEN** exactly one inactive schema is created for the publication identity
- **AND** the relational publication reaches completed state

#### Scenario: Completion fails after schema creation
- **WHEN** the schema exists but publication completion was interrupted
- **THEN** recovery resolves the same schema idempotently
- **AND** does not create a duplicate publication
