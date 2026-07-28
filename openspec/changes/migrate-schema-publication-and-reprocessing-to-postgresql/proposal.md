## Why

Evaluation, publication, and reprocessing form the second half of the schema-draft workflow and depend on durable draft revisions plus document and schema ownership. Migrating them after draft analysis avoids a single oversized change and allows worker claims and recovery semantics to be designed around PostgreSQL.

## What Changes

- Require PostgreSQL-owned draft analysis, knowledge bases, schemas, documents, and run history.
- Add Flyway-managed evaluation run/outcome, publication, reprocessing plan, and reprocessing item tables with revision-specific uniqueness, status checks, retry counters, claims, and worker indexes.
- Replace corresponding Neo4j nodes, relationships, repositories, and ownership queries with JPA entities, relational adapters, and conditional SQL updates.
- Preserve held-out evaluation, deterministic durable outcomes, publication readiness, inactive-schema creation, activation-triggered bounded overwrite planning, retry behavior, recovery, counters, and API contracts.
- Coordinate graph and document-processing effects through committed relational state transitions and idempotent external work.
- Keep JSON payloads as text and leave JSONB optimization outside scope.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `schema-draft-evaluation`: Persist revision-specific evaluation runs and outcomes relationally with recoverable claims.
- `schema-draft-publication`: Persist readiness and publication identity relationally while preserving inactive-schema publication.
- `schema-reprocessing-plans`: Persist bounded plans, item claims, retries, counters, and recovery in PostgreSQL.
- `schema-draft-lifecycle`: Derive complete workflow summaries from relational analysis, evaluation, publication, and reprocessing state.
- `application-workflow-orchestration`: Apply durable checkpoints and idempotent effects to publication and reprocessing.

## Impact

- Affects evaluation/publication/reprocessing models, repositories, services, workers, recovery scheduling, workflow navigation projections, Flyway migrations, transaction boundaries, and integration tests.
- Completes migration of draft operational state but does not yet remove every legacy Neo4j initializer or operational label.
