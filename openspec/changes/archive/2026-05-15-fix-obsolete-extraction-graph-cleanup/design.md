## Context

`GraphWriteService` records extracted node provenance through `(:ExtractionRun)-[:CREATED_NODE]->(node)` relationships, but extracted domain relationships store provenance as properties (`sourceDocumentId`, `extractionRunId`) on the relationship itself. The previous cleanup query deleted only relationships attached directly to deleted `ExtractionRun` nodes and then deleted candidate nodes only when they had no remaining relationships. In live data, stale extracted nodes could remain connected to other stale extracted nodes and therefore survived cleanup.

This change has already been implemented in the working tree. These artifacts document the applied behavior so it can be reviewed and archived through OpenSpec.

## Goals / Non-Goals

**Goals:**
- Remove graph relationships produced by deleted extraction runs.
- Remove extracted nodes for the same document that no longer have retained `CREATED_NODE` provenance from an existing extraction run.
- Preserve extracted nodes that are still linked to a retained extraction run.
- Cover stale related-node cleanup in integration tests.

**Non-Goals:**
- Perform a one-time cleanup of already-stale data in a running database.
- Change how graph extraction writes node or relationship provenance.
- Introduce historical extraction archives or soft deletes.

## Decisions

1. Delete stale domain relationships by `sourceDocumentId` and `extractionRunId`.
- Rationale: extracted relationships are not connected to `ExtractionRun` nodes, so direct run deletion cannot remove them.
- Alternative considered: add `CREATED_RELATIONSHIP` relationships from runs to extracted relationships.
- Rejected for this fix because Neo4j relationships cannot be targeted by relationships, and introducing relationship nodes would be a larger data model change.

2. Delete stale extracted nodes based on missing retained `CREATED_NODE` provenance.
- Rationale: a node can still have stale graph relationships to other stale nodes, so degree-zero checks are insufficient.
- Alternative considered: delete only degree-zero nodes.
- Rejected because it preserves stale connected subgraphs.

3. Keep cleanup document-scoped.
- Rationale: stale graph relationship and node deletion must not cross document boundaries.
- Alternative considered: global stale-node sweep.
- Rejected because global cleanup has broader blast radius and is unnecessary for extraction-run cleanup.

## Risks / Trade-offs

- [Risk] Nodes without `CREATED_NODE` provenance but still needed by another retained run could be deleted.
- Mitigation: deletion is scoped to nodes with the target `sourceDocumentId` and no retained extraction-run provenance; tests cover retained-node preservation.

- [Risk] Existing stale data remains until a future cleanup path runs or a one-time repair is executed.
- Mitigation: document this as out of scope for the application behavior fix.

## Migration Plan

1. Deploy the cleanup query change.
2. Verify extraction retry and overwrite cleanup integration tests pass.
3. If needed, run a separate operator-approved cleanup for already-stale live data.

Rollback strategy:
- Revert the cleanup query to the previous direct-run-relationship and degree-zero cleanup behavior.

## Open Questions

- None.
