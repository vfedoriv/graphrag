## ADDED Requirements

### Requirement: Draft external effects use relational checkpoints
The system SHALL commit draft/source/run intent before filesystem or model work and SHALL commit results and terminal status afterward in separate relational transactions.

#### Scenario: A model call fails
- **WHEN** analysis intent is committed but the source model call fails
- **THEN** the failure and retry eligibility are durably recorded
- **AND** the draft claim can be recovered

#### Scenario: A source file operation is repeated
- **WHEN** recovery retries an already-applied filesystem mutation
- **THEN** the operation is handled idempotently
- **AND** the relational journal reaches a consistent terminal state
