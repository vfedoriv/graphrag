## ADDED Requirements

### Requirement: PostgreSQL is authoritative for AI profiles
The system SHALL persist AI profile identity, provider configuration, default selection, secret state, and optimistic version in PostgreSQL without changing existing profile API contracts.

#### Scenario: A profile is created
- **WHEN** a valid profile request is submitted
- **THEN** the profile is committed relationally with its assigned identifier
- **AND** subsequent reads return no API-key value

#### Scenario: Concurrent profile mutation occurs
- **WHEN** a caller writes using a stale profile version
- **THEN** the relational update is rejected as a conflict
- **AND** the stored profile remains unchanged

### Requirement: Exactly one default profile is selected safely
The system SHALL enforce at most one default AI profile through a database constraint and SHALL seed a configured default idempotently when none exists.

#### Scenario: Concurrent default creation occurs
- **WHEN** concurrent startup or API operations attempt to create different default profiles
- **THEN** the database prevents multiple defaults
- **AND** the service resolves the surviving default deterministically
