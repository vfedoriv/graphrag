## ADDED Requirements

### Requirement: Shared destructive reprocessing exclusion
The reprocessing subsystem SHALL allow only one queued or running destructive plan per knowledge base across schema activation and chunk strategy migration while preserving all existing schema-plan request, progress, retry, and navigation behavior.

#### Scenario: Schema plan already active
- **WHEN** a chunk migration is requested for a knowledge base with an active schema reprocessing plan
- **THEN** the request returns `409 Conflict` and creates no competing plan

#### Scenario: Chunk plan already active
- **WHEN** a schema reprocessing plan is requested for a knowledge base with an active chunk migration
- **THEN** the request returns `409 Conflict` and creates no competing plan
