## Why

Document metadata and run history coordinate filesystem mutations, processing, extraction, retries, and graph cleanup. PostgreSQL ownership is needed to make these workflows durable without pretending a Neo4j or filesystem operation participates in one atomic transaction.

## What Changes

- Require the relational foundation and PostgreSQL-owned knowledge bases and schemas.
- Add Flyway-managed document upload, processing run, extraction run, and storage mutation tables with uniqueness, lifecycle checks, optimistic versions, and worker/recovery indexes.
- Replace operational Neo4j document and run persistence with relational adapters while keeping `DocumentChunk` graph persistence in Neo4j.
- Implement cross-store workflows as committed PostgreSQL intent, idempotent filesystem or graph work, and committed PostgreSQL completion, retaining retryable records after interruption.
- Preserve SHA-256 deduplication, upload/list/replace/delete/process/chunks APIs, pagination, overwrite behavior, run history, recovery, and document-scoped artifact cleanup.
- Make lifecycle checkpoints use independent relational transactions and keep cross-store orchestrators unannotated.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `document-management`: Make PostgreSQL authoritative for document identity and metadata while preserving storage and API behavior.
- `document-processing-run-history`: Persist processing and extraction lifecycle history relationally with durable status transitions.
- `document-storage-reconciliation`: Store retryable filesystem mutation journals in PostgreSQL and reconcile them idempotently.
- `extraction-run-cleanup`: Resolve run state relationally and retain cleanup by stable run identifiers.
- `application-workflow-orchestration`: Apply explicit intent/external-effect/completion semantics to document cross-store workflows.

## Impact

- Affects document and run domain models, repositories, upload/processing/storage services, filesystem reconciliation, graph cleanup coordination, Flyway migrations, transaction boundaries, and Testcontainers integration tests.
- Public API contracts remain unchanged; existing GraphRAG data is not migrated.
