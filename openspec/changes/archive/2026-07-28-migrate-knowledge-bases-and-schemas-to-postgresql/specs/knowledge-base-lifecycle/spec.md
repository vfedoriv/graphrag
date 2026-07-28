## ADDED Requirements

### Requirement: Knowledge-base metadata is relational operational state
The system SHALL persist knowledge-base identity, lifecycle metadata, and AI profile association in PostgreSQL while preserving existing create, read, list, and delete contracts.

#### Scenario: A knowledge base is created
- **WHEN** a valid create request is accepted
- **THEN** its relational record references the selected default AI profile
- **AND** its public representation is unchanged

#### Scenario: A knowledge base is concurrently modified
- **WHEN** a stale caller attempts a mutation
- **THEN** optimistic concurrency rejects the stale mutation
- **AND** the committed record remains intact
