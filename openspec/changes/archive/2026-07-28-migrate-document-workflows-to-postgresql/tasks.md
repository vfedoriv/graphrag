## 1. Prerequisites and Relational Model

- [x] 1.1 Verify relational KB, schema, and profile ownership is complete
- [x] 1.2 Add Flyway migrations for document uploads, processing runs, extraction runs, and document storage mutations
- [x] 1.3 Add uniqueness, status checks, non-cascading mutation journals, optimistic versions, active-completion constraints, and recovery/worker indexes
- [x] 1.4 Add repository tests for deduplication scope, lifecycle transitions, overwrite uniqueness, claims, rollback, pagination, and optimistic conflicts

## 2. Document Metadata and Storage

- [x] 2.1 Add relational document and storage-mutation entities, repositories, adapters, and API/domain mappings
- [x] 2.2 Migrate upload, list, chunk-list metadata, replacement, and deletion coordination to relational ownership
- [x] 2.3 Migrate filesystem mutation creation and reconciliation to independent relational checkpoints with idempotent external operations
- [x] 2.4 Verify interrupted upload, replacement, and deletion converge without affecting unrelated documents or knowledge bases

## 3. Processing and Extraction Runs

- [x] 3.1 Add relational processing/extraction run entities, repositories, history projections, and lifecycle checkpoint collaborators
- [x] 3.2 Migrate processing start, success, failure, retry, overwrite, and recovery transitions to qualified `REQUIRES_NEW` relational transactions
- [x] 3.3 Keep cross-store processing orchestrators unannotated and invoke graph/file operations through store-specific collaborators
- [x] 3.4 Migrate cleanup coordination to resolve failed, stale, and replaced run IDs from PostgreSQL

## 4. Verification

- [x] 4.1 Update document and processing integration tests for PostgreSQL metadata plus Neo4j chunk/fact persistence
- [x] 4.2 Verify completion-commit failure, partial evidence cleanup, retry, overwrite, and storage reconciliation paths
- [x] 4.3 Run focused document/processing tests, the canonical end-to-end test, and `./mvnw test`
