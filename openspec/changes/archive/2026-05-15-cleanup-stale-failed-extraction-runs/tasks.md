## 1. Cleanup Query And Persistence

- [x] 1.1 Identify extraction-run node labels/properties and relationship patterns currently used for run persistence.
- [x] 1.2 Add repository/Neo4j cleanup operation that deletes failed runs for a document after successful run completion.
- [x] 1.3 Ensure cleanup operation also removes now-orphaned nodes (degree `0`) created by failed-run deletion.
- [x] 1.4 Return cleanup counts (deleted runs, deleted relationships/nodes) for logging and verification.

## 2. Service Integration

- [x] 2.1 Invoke cleanup from successful extraction flow after run status is persisted as `COMPLETED`.
- [x] 2.2 Keep cleanup skipped on failed/in-progress runs.
- [x] 2.3 Add structured logs with run/document context and cleanup counts.

## 3. Tests

- [x] 3.1 Add integration test for failed-run retry path proving failed run is removed after later success.
- [x] 3.2 Add test proving orphaned nodes created by failed-run cleanup are deleted.
- [x] 3.3 Add test proving nodes shared with completed run are preserved.
- [x] 3.4 Run targeted extraction and persistence test suite.
