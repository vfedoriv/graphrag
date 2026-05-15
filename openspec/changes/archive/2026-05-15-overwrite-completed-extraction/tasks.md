## 1. API Contract and Validation

- [x] 1.1 Add `allowOverwrite` boolean to the extraction request contract with default `false` behavior when omitted.
- [x] 1.2 Implement pre-flight guard that checks for existing `COMPLETED` runs per document and rejects new extraction unless `allowOverwrite=true`.
- [x] 1.3 Return a clear RFC 7807 `409 Conflict` response that explains overwrite confirmation is required.

## 2. Overwrite Cleanup Flow

- [x] 2.1 Update extraction orchestration so completed-run cleanup is triggered only after the new run is persisted as `COMPLETED`.
- [x] 2.2 Implement repository cleanup for prior completed runs scoped to the same document, excluding the current successful run.
- [x] 2.3 Remove relationships attached to deleted completed run nodes within the same cleanup transaction.
- [x] 2.4 Remove nodes that become orphaned due to overwrite cleanup while preserving nodes still connected to retained graph data.

## 3. Concurrency and Safety

- [x] 3.1 Ensure overwrite-gate and cleanup logic are transactionally safe for near-concurrent overwrite requests on the same document.
- [x] 3.2 Add safeguards/tests that prevent cleanup from deleting nodes outside the document-scoped cleanup candidate set.

## 4. Tests and Documentation

- [x] 4.1 Add/extend integration tests for: overwrite denied without flag, overwrite allowed with flag, and no cleanup on overwrite failure.
- [x] 4.2 Add/extend integration tests for successful overwrite cleanup verifying old completed run deletion, relationship deletion, and orphan cleanup/preservation.
- [x] 4.3 Update API docs/examples to document `allowOverwrite` and overwrite semantics.
