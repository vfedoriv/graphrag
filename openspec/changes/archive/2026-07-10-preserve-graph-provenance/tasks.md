## 1. Evidence Model and Migration

- [x] 1.1 Define persisted node and relationship evidence records, identifiers, indexes, and canonical-fact references.
- [x] 1.2 Implement an idempotent, resumable migration that backfills evidence from legacy provenance fields and reports validation counts.
- [x] 1.3 Add transitional compatibility reads and migration-state observability without deleting legacy provenance prematurely.

## 2. Graph Write and Cleanup

- [x] 2.1 Update graph writes to create canonical facts and immutable evidence records without overwriting source provenance.
- [x] 2.2 Update document replacement and deletion cleanup to remove target evidence before conditionally deleting unsupported facts.
- [x] 2.3 Update retry and overwrite cleanup to remove stale-run evidence while retaining facts supported by other runs or documents.
- [x] 2.4 Define deterministic handling for conflicting canonical and source-specific relationship properties.

## 3. Verification

- [x] 3.1 Add integration coverage for two documents asserting the same node and relationship, then deleting either document.
- [x] 3.2 Add integration coverage for failed retries and overwrite cleanup with shared retained facts.
- [x] 3.3 Add migration and rollback-path tests, then run the focused graph cleanup tests and the full Maven suite.
