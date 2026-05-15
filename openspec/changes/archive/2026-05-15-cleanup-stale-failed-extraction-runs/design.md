## Context

`GraphExtractionService` writes extraction-run nodes and links extracted graph entities/relationships to those runs. On retry, a failed run remains in the graph even after a later successful run for the same document. This leaves stale run nodes and stale run-linked relationships in Neo4j.

The requested behavior is to clean up old failed run nodes once a successful run exists, including deleting nodes that become isolated after removing the failed-run subgraph.

## Goals / Non-Goals

**Goals:**
- Remove prior failed extraction run nodes for a document after a successful run is persisted.
- Remove relationships attached to removed failed run nodes.
- Remove nodes that become orphaned due to this cleanup (no remaining relationships).
- Keep cleanup safe so shared nodes still connected to other runs/subgraphs remain.
- Make cleanup idempotent and test-covered.

**Non-Goals:**
- Deleting failed runs when there is no successful replacement run.
- Broad graph garbage collection unrelated to extraction-run cleanup.
- Rewriting extraction data model or adding external cleanup jobs.

## Decisions

1. Run cleanup immediately after successful extraction persistence.
- Decision: Trigger cleanup at end of successful extraction flow, scoped by `documentId`.
- Rationale: Keeps graph tidy in the same lifecycle and avoids separate schedulers.

2. Delete only failed runs older than the current successful run.
- Decision: Match failed runs for the same document and remove them when current run status is `COMPLETED`.
- Rationale: Preserves only latest relevant state while avoiding deleting active/in-progress runs.

3. Remove newly orphaned nodes after failed-run detachment.
- Decision: After deleting failed run nodes/relationships, delete nodes with degree `0` that were part of the cleanup scope.
- Rationale: Prevents dead-end artifacts while protecting shared nodes still connected elsewhere.

4. Implement with explicit Cypher in repository/service boundary.
- Decision: Add repository-level cleanup query with returned counts for observability logs.
- Rationale: Single transactional operation is safer and easier to reason about than iterative deletes.

## Risks / Trade-offs

- [Risk] Over-aggressive orphan deletion could remove shared domain nodes. → Mitigation: only delete nodes proven to have zero relationships after detach.
- [Risk] Cleanup query complexity may impact performance on large graphs. → Mitigation: scope by document and status, and index run/document lookup fields.
- [Risk] Concurrent retries may race cleanup. → Mitigation: perform in write transaction after success and keep operation idempotent.
