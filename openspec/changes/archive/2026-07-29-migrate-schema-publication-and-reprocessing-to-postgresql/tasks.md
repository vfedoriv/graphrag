## 1. Prerequisites and Relational Schema

- [x] 1.1 Verify PostgreSQL-owned draft analysis, KBs, schemas, documents, and run history are complete
- [x] 1.2 Add Flyway migrations for evaluation runs/outcomes, publications, reprocessing plans, and reprocessing items
- [x] 1.3 Add revision/reuse/publication/item uniqueness, enum checks, claims, retries, counters, optimistic versions, and worker/recovery indexes
- [x] 1.4 Add repository tests for conditional claims, revision identity, ownership, retry eligibility, counter repair, and optimistic conflicts

## 2. Evaluation and Publication

- [x] 2.1 Add relational evaluation entities, outcome mappings, repositories, and recovery projections
- [x] 2.2 Migrate evaluation eligibility, held-out execution checkpoints, durable outcomes, reuse, failure categories, and stale-run recovery
- [x] 2.3 Add relational publication entities and migrate revision-specific readiness and inactive-schema publication
- [x] 2.4 Make schema creation and publication completion idempotent when interrupted between checkpoints

## 3. Reprocessing

- [x] 3.1 Add relational plan/item entities, repositories, bounded projections, and conditional worker claim operations
- [x] 3.2 Migrate activation-triggered bounded plan creation and unique document item generation
- [x] 3.3 Migrate item execution, retries, ownership, counters, lifecycle summaries, and recovery around document overwrite processing
- [x] 3.4 Repair derived plan counters from authoritative item state and verify interrupted-worker recovery

## 4. Cleanup and Verification

- [x] 4.1 Complete relational draft workflow navigation across analysis, evaluation, publication, and reprocessing
- [x] 4.2 Remove runtime callers of migrated evaluation/publication/reprocessing Neo4j repositories while deferring broad label cleanup
- [x] 4.3 Run focused workflow tests, the canonical end-to-end test, and `./mvnw test`
