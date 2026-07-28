## ADDED Requirements

### Requirement: Processing and extraction run history is relational
The system SHALL persist assigned run IDs, document ownership, lifecycle state, retry metadata, timestamps, and optimistic versions in PostgreSQL.

#### Scenario: Processing starts and completes
- **WHEN** document processing is accepted
- **THEN** a `RUNNING` relational checkpoint commits before graph work
- **AND** a `COMPLETED` checkpoint commits only after required external work succeeds

#### Scenario: Processing is interrupted
- **WHEN** a run remains stale in a non-terminal state
- **THEN** recovery marks it failed or retryable according to existing policy
- **AND** its history remains queryable through the existing API

### Requirement: Completed processing uniqueness is enforced
The system SHALL prevent more than one active completed processing result for a document while preserving explicit overwrite behavior and historical run visibility.

#### Scenario: A successful document is processed without overwrite
- **WHEN** a completed active result already exists and overwrite is not authorized
- **THEN** processing is rejected without creating a second active completed result
