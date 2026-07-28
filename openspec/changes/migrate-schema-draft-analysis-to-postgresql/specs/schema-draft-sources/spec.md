## ADDED Requirements

### Requirement: Draft sources and revisions are relationally owned
The system SHALL persist source identity, draft ownership, immutable source revisions, content metadata, and currentness in PostgreSQL with unique revision sequences.

#### Scenario: A source revision is added
- **WHEN** owned source content changes
- **THEN** a new immutable relational revision is appended
- **AND** prior revision history remains available

#### Scenario: A source belongs to another draft
- **WHEN** a caller addresses the source through a different draft or knowledge base
- **THEN** the operation is rejected without disclosing or changing the source

### Requirement: Source storage mutations remain recoverable
The system SHALL retain relational draft-storage mutation journals independently of cascading source deletion until filesystem state is reconciled.

#### Scenario: Source deletion is interrupted
- **WHEN** source metadata changes but file cleanup fails
- **THEN** a durable journal supports idempotent recovery
