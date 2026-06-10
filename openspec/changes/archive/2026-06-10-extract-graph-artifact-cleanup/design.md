## Context

Document replacement/deletion and extraction-run completion both remove document-scoped graph artifacts. Today the cleanup Cypher lives inside `DocumentUploadService` and `GraphExtractionService`, which mixes orchestration with low-level graph mutation details. The behavior is important and already covered by integration tests, so the refactor should centralize implementation without changing semantics.

## Goals / Non-Goals

**Goals:**
- Move graph artifact cleanup Cypher into a dedicated component.
- Preserve current cleanup semantics and count reporting.
- Give orchestration services a smaller, intention-revealing API.
- Keep integration tests focused on externally visible cleanup behavior.

**Non-Goals:**
- Changing which artifacts are deleted.
- Changing overwrite rules.
- Replacing Neo4j Cypher with Spring Data relationship mapping.
- Changing document upload, replacement, or deletion API contracts.

## Decisions

- Introduce a `GraphArtifactCleanupService` or similarly named component.
  - Rationale: cleanup spans document chunks, extraction runs, graph relationships, and extracted nodes; it is a graph persistence concern rather than upload business logic.
- Provide separate methods for distinct cleanup use cases.
  - `cleanupDocumentArtifacts(documentId)` for replacement/deletion.
  - `cleanupRunsAfterSuccessfulExtraction(documentId, currentRunId, allowOverwrite)` for failed/overwritten run cleanup.
  - Rationale: the current Cypher blocks have different entry points and semantics.
- Return typed cleanup result records.
  - Rationale: existing logging and tests need counts for deleted chunks, runs, relationships, and obsolete nodes.
- Keep direct `Neo4jClient` inside the cleanup component.
  - Rationale: these graph mutations are easier and safer as explicit Cypher than as repository entity traversal.

## Risks / Trade-offs

- Refactor could alter cleanup semantics -> Preserve the current Cypher behavior first, then refactor tests around the new component.
- Cleanup component could become a graph dumping ground -> Limit it to document-scoped derived artifacts and extraction-run cleanup.
- Transaction boundaries could shift -> Keep callers transactional where needed or annotate cleanup methods consistently with existing behavior.
