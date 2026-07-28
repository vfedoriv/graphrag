## Context

Document upload metadata, processing runs, extraction runs, and filesystem mutation journals coordinate three resource types: PostgreSQL state, local binaries, and Neo4j graph artifacts. A transaction cannot cover all three. The existing services already use lifecycle checkpoints and reconciliation patterns, but their authoritative records are Neo4j nodes.

## Goals / Non-Goals

**Goals:**

- Move document and run authority to PostgreSQL.
- Preserve upload, replacement, deletion, processing, overwrite, history, and recovery behavior.
- Make cross-store failure states explicit, durable, and retryable.

**Non-Goals:**

- Moving chunks or extracted facts out of Neo4j.
- Introducing distributed transactions or exactly-once external effects.
- Migrating existing GraphRAG data.

## Decisions

1. **Create four relational aggregates.** `document_upload` references its KB and enforces unique `(knowledge_base_id, sha256)`. `document_processing_run` and `extraction_run` preserve lifecycle history and stable IDs. `document_storage_mutation` is a durable journal without a cascading target foreign key so cleanup intent survives target deletion.

2. **Use state constraints and indexes.** Enum strings receive database checks. A partial uniqueness rule preserves one active completed processing result per document. Status, retry, ownership, creation-time, and recovery indexes support bounded workers.

3. **Separate cross-store phases.**

   ```text
   relational intent/RUNNING commit
                 │
                 ▼
       idempotent file or graph work
                 │
                 ▼
   relational completion/FAILED commit
   ```

   Lifecycle collaborators use `REQUIRES_NEW` relational transactions. Orchestrators remain unannotated and call proxied relational and graph collaborators.

4. **Retain identifiers for cleanup.** PostgreSQL is authoritative for document and run IDs copied into graph artifacts. Interrupted processing can identify partial evidence by extraction run ID even after operational run nodes disappear from Neo4j.

5. **Keep journals until reconciliation succeeds.** Replacement and deletion never cascade away pending filesystem intent. Reconciliation treats missing files and already-removed graph artifacts idempotently.

## Risks / Trade-offs

- [Failure after graph work but before completion commit] → Recovery marks stale runs retryable/failed and removes partial evidence by stable run ID.
- [Failure after binary write but before metadata update] → Persist mutation intent first and reconcile using non-cascading journal records.
- [A retry duplicates graph facts] → Use run-scoped evidence, idempotent graph writes, and evidence-aware cleanup.
- [Intermediate graph queries depend on document nodes] → Preserve copied IDs until the graph-data-plane change removes anchors comprehensively.

## Migration Plan

1. Add document, run, and mutation tables with constraints and indexes.
2. Introduce relational adapters and lifecycle checkpoint collaborators.
3. Migrate upload/list/replace/delete and storage reconciliation.
4. Migrate processing/extraction run history, overwrite, retry, and recovery.
5. Update graph cleanup coordination and regression tests.
6. Defer direct chunk/evidence scope and removal of graph anchors to `isolate-neo4j-graph-data-plane`.

Rollback is limited to pre-cutover empty stores. Pending mutation records must be reconciled before reverting application code.

## Open Questions

- None.
