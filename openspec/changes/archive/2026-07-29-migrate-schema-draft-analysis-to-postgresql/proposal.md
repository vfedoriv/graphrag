## Why

Schema draft analysis is a large operational workflow with revisions, claims, retries, conflicts, decisions, and filesystem-backed sources. Moving this cohesive half of the draft subsystem separately keeps the migration reviewable while establishing the relational history consumed by evaluation and publication.

## What Changes

- Require PostgreSQL-owned knowledge bases, schemas, and documents.
- Add Flyway-managed draft, source, source revision, analysis run, source result, aggregate revision, conflict, decision, and draft storage mutation tables.
- Replace draft-analysis Neo4j nodes, ownership relationships, repositories, and lease nodes with JPA entities, foreign keys, relational projections, and an atomic conditional analysis claim.
- Preserve bounded source ownership, deterministic aggregation, conflict handling, revision currentness, retry lineage, recovery, review decisions, workflow navigation, and existing API contracts.
- Preserve raw JSON and text payload representations to maintain existing hashes and fingerprints; JSONB conversion remains outside scope.
- Keep evaluation, publication, and reprocessing persistence outside this change.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `schema-draft-lifecycle`: Make PostgreSQL authoritative for draft identity, revision lineage, ownership, status, and navigation summaries.
- `schema-draft-sources`: Persist owned sources, immutable revisions, and filesystem mutation journals relationally.
- `schema-draft-analysis`: Use relational claims, runs, source results, aggregates, recovery, and retries.
- `schema-draft-review`: Persist conflicts and ordered review decisions with relational consistency.
- `multi-source-schema-discovery`: Read and write bounded discovery workflow state through PostgreSQL while preserving deterministic aggregation.
- `application-workflow-orchestration`: Apply durable relational checkpoints around source-file and model-call workflow effects.

## Impact

- Affects draft domain models, repositories, lifecycle/source/analysis/review/navigation services, storage reconciliation, scheduled recovery, Flyway migrations, transaction boundaries, and draft integration tests.
- Evaluation, publication, reprocessing, public API shapes, and generated-schema review semantics remain unchanged.
