## ADDED Requirements

### Requirement: Relational run state coordinates idempotent graph work
The system SHALL use PostgreSQL run state to authorize, recover, and complete graph writes while keeping graph mutations in graph-specific transactions.

#### Scenario: Extraction graph work succeeds
- **WHEN** a relational extraction run is `RUNNING` and graph persistence completes
- **THEN** relational completion is committed in a later checkpoint

#### Scenario: Extraction graph work is retried
- **WHEN** a prior attempt left run-scoped evidence
- **THEN** recovery removes or safely reuses artifacts by stable run identity
- **AND** retry does not depend on a Neo4j operational run node
