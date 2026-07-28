## ADDED Requirements

### Requirement: Evaluation runs and outcomes are relational
The system SHALL persist revision-specific evaluation runs, claims, held-out inputs, deterministic metrics, outcomes, retries, and terminal state in PostgreSQL.

#### Scenario: An eligible revision is evaluated
- **WHEN** held-out evaluation starts
- **THEN** a relational run checkpoint commits before model extraction
- **AND** the durable outcome commits only after calculation succeeds

#### Scenario: An equivalent completed evaluation exists
- **WHEN** the same eligible revision and evaluation identity are requested
- **THEN** the system reuses the durable outcome according to existing policy

#### Scenario: Evaluation is interrupted
- **WHEN** recovery observes a stale claimed run
- **THEN** conditional recovery marks it retryable or terminal without overwriting a newer owner
