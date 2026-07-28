## 1. Prerequisites and Relational Schema

- [ ] 1.1 Verify PostgreSQL-owned draft analysis, KBs, schemas, documents, and run history are complete
- [ ] 1.2 Add Flyway migrations for evaluation runs/outcomes, publications, reprocessing plans, and reprocessing items
- [ ] 1.3 Add revision/reuse/publication/item uniqueness, enum checks, claims, retries, counters, optimistic versions, and worker/recovery indexes
- [ ] 1.4 Add repository tests for conditional claims, revision identity, ownership, retry eligibility, counter repair, and optimistic conflicts

## 2. Evaluation and Publication

- [ ] 2.1 Add relational evaluation entities, outcome mappings, repositories, and recovery projections
- [ ] 2.2 Migrate evaluation eligibility, held-out execution checkpoints, durable outcomes, reuse, failure categories, and stale-run recovery
- [ ] 2.3 Add relational publication entities and migrate revision-specific readiness and inactive-schema publication
- [ ] 2.4 Make schema creation and publication completion idempotent when interrupted between checkpoints

## 3. Reprocessing

- [ ] 3.1 Add relational plan/item entities, repositories, bounded projections, and conditional worker claim operations
- [ ] 3.2 Migrate activation-triggered bounded plan creation and unique document item generation
- [ ] 3.3 Migrate item execution, retries, ownership, counters, lifecycle summaries, and recovery around document overwrite processing
- [ ] 3.4 Repair derived plan counters from authoritative item state and verify interrupted-worker recovery

## 4. Cleanup and Verification

- [ ] 4.1 Complete relational draft workflow navigation across analysis, evaluation, publication, and reprocessing
- [ ] 4.2 Remove runtime callers of migrated evaluation/publication/reprocessing Neo4j repositories while deferring broad label cleanup
- [ ] 4.3 Run focused workflow tests, the canonical end-to-end test, and `./mvnw test`
