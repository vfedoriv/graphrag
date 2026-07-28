## ADDED Requirements

### Requirement: Storage mutation journals survive target lifecycle changes
The system SHALL store document filesystem mutation journals in PostgreSQL without cascading deletion from their target documents and SHALL retry them idempotently.

#### Scenario: A target document record is deleted
- **WHEN** an outstanding delete mutation still requires filesystem cleanup
- **THEN** the journal remains available until reconciliation reaches a terminal success

#### Scenario: Reconciliation repeats completed external work
- **WHEN** the binary has already reached the intended state
- **THEN** reconciliation treats the external step as successful and completes the journal
