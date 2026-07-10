## Context

Canonical extracted nodes and relationships are merged across documents, but their `sourceDocumentId`, `sourceChunkIds`, and `extractionRunId` properties are overwritten by the last writer. Cleanup treats those properties as ownership metadata. This confuses fact identity with evidence identity and makes cleanup unsafe when facts are shared.

## Goals / Non-Goals

**Goals:**
- Retain every document, chunk, and extraction-run assertion of a canonical node or relationship.
- Make document deletion, replacement, retry, and overwrite cleanup safe for facts with retained evidence.
- Preserve canonical graph traversal through domain nodes and typed relationships.
- Provide an idempotent migration from existing provenance fields.

**Non-Goals:**
- Change schema-constrained extraction rules or canonical node/relationship identifiers.
- Rebuild all extracted graph data unless migration validation detects irreparable legacy data.
- Introduce asynchronous document processing.

## Decisions

### Store evidence as separate graph records

Persist immutable node and relationship evidence records keyed by extraction run, source chunk, and canonical fact ID. Canonical domain nodes and typed relationships remain the query surface and no longer carry mutable single-source ownership fields. This keeps existing graph queries stable while giving cleanup a precise ownership target.

An alternative of accumulating document and chunk IDs in relationship properties is rejected: it is difficult to update atomically, loses per-run metadata, grows unbounded, and makes concurrent cleanup unsafe.

### Remove canonical facts only after evidence is gone

Cleanup first deletes evidence records for its target document or stale run. It then removes a canonical relationship or node only when no retained evidence records reference that canonical fact. This replaces filtering by mutable `sourceDocumentId` and `extractionRunId` properties.

### Keep a transitional compatibility read path during migration

Migration backfills one evidence record from each legacy fact's stored provenance, marks migrated records, and retains legacy fields until validation confirms the migration. New writes use evidence records exclusively. Rollback disables the new write/read path without deleting backfilled evidence.

## Risks / Trade-offs

- [More graph records and cleanup traversal] → Add indexes for evidence IDs, document IDs, run IDs, and canonical fact IDs; verify plans with integration tests.
- [Legacy provenance represents only the last writer] → Preserve the known legacy assertion and document the historical limitation; do not invent missing evidence.
- [Canonical relationship property conflicts across evidence] → Define deterministic fact-property precedence and retain source-specific values on evidence records.
- [Migration interruption] → Make migration idempotent and resumable with explicit progress/validation markers.

## Migration Plan

1. Add the evidence data model, indexes, and dual-read validation without changing cleanup.
2. Backfill evidence from legacy extracted-node and extracted-relationship provenance in batches.
3. Verify evidence counts and referential integrity, then switch all writes and cleanup to evidence ownership.
4. Remove transitional legacy provenance reads after a release with monitored migration completion.

## Open Questions

- Which relationship properties are canonical facts versus source-specific evidence when documents disagree?
- Should the API expose provenance details directly or only use them internally for cleanup and retrieval?
