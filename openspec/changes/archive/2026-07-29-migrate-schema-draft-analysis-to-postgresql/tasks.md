## 1. Prerequisites and Draft Schema

- [x] 1.1 Verify relational KB, schema, document, and run ownership is complete
- [x] 1.2 Add Flyway migrations for drafts, sources, source revisions, analysis runs, source results, aggregate revisions, conflicts, decisions, and draft storage mutations
- [x] 1.3 Add foreign keys, non-cascading mutation journals, revision/sequence/reuse uniqueness, optimistic versions, enum checks, and worker/navigation indexes
- [x] 1.4 Add relational constraint and repository tests, including deterministic text-payload persistence

## 2. Draft Lifecycle and Sources

- [x] 2.1 Add relational draft/source/revision/mutation entities, repositories, adapters, and bounded navigation projections
- [x] 2.2 Migrate draft lifecycle and ownership-safe CRUD to relational transactions
- [x] 2.3 Migrate text/file/document source revision handling and storage mutation reconciliation
- [x] 2.4 Verify source history, currentness, ownership rejection, and interrupted filesystem recovery

## 3. Analysis and Review

- [x] 3.1 Replace the analysis lease node with a conditional update of `schema_draft.running_analysis_run_id`
- [x] 3.2 Migrate analysis runs, per-source results, aggregation, retry lineage, reuse, and stale-claim recovery
- [x] 3.3 Migrate conflicts and ordered review decisions with relational uniqueness and optimistic conflict handling
- [x] 3.4 Preserve deterministic hashes/fingerprints and review-only generated-schema behavior
- [x] 3.5 Update draft navigation summaries without N+1 queries while keeping downstream evaluation/publication fields compatible

## 4. Cleanup and Verification

- [x] 4.1 Remove runtime callers of migrated draft-analysis Neo4j repositories while retaining downstream adapters until the next change
- [x] 4.2 Verify concurrency, stale-worker completion, retry, recovery, conflict, decision, and storage reconciliation paths
- [x] 4.3 Run focused draft/discovery integration tests and `./mvnw test`
