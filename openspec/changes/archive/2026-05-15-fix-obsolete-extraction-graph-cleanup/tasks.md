## 1. Cleanup Query

- [x] 1.1 Inspect live Neo4j data to identify stale extracted nodes without retained `CREATED_NODE` provenance.
- [x] 1.2 Update cleanup to delete extracted graph relationships by `sourceDocumentId` and deleted-run `extractionRunId`.
- [x] 1.3 Update cleanup to delete extracted nodes for the document that no longer have retained extraction-run provenance.
- [x] 1.4 Keep cleanup scoped to the document being processed.

## 2. Repository Cleanup

- [x] 2.1 Remove duplicated stale cleanup query from `ExtractionRunRepository`.
- [x] 2.2 Keep `hasCompletedRun` repository behavior unchanged.

## 3. Tests

- [x] 3.1 Extend cleanup integration test schema with a related node type and relationship.
- [x] 3.2 Add test data for a stale related node and stale domain relationship from a deleted run.
- [x] 3.3 Assert stale related node and relationship are removed while retained-run data survives.
- [x] 3.4 Run focused and broader Neo4j integration tests.
