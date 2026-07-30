## 1. Generalized Plan Model

- [ ] 1.1 Add reprocessing reason, typed chunk selection/target, item outcome, and immutable snapshot domain contracts.
- [ ] 1.2 Add PostgreSQL migrations and mappers for generalized plan reason, target identities, canonical snapshot, selection, and new outcomes.
- [ ] 1.3 Backfill existing rows as schema reprocessing plans and preserve existing schema DTO and navigation behavior.
- [ ] 1.4 Add a transactional one-destructive-plan-per-knowledge-base conflict guard across schema and chunk plans.

## 2. Chunk Migration Execution

- [ ] 2.1 Implement `OUTDATED_STRATEGY`, owned non-empty `DOCUMENT_IDS`, and forced `ALL` selection with expected-revision validation.
- [ ] 2.2 Atomically snapshot chunk/tokenizer/header/parser/profile/embedding/schema/options targets and document hashes at plan creation.
- [ ] 2.3 Pass immutable plan inputs into overwrite processing instead of resolving behavior-affecting live settings.
- [ ] 2.4 Implement `STALE_SOURCE` and `BLOCKED_TARGET_CHANGED` transitions while retaining independently committed successes.
- [ ] 2.5 Extend progress, history, recovery, and linked retry to resnapshot explicitly unresolved documents.

## 3. API and Verification

- [ ] 3.1 Extend the existing reprocessing endpoint and RFC 7807 conflicts without breaking schema-plan clients.
- [ ] 3.2 Report effective chunker revision and explicit migration lifecycle through runtime settings without automatic plan creation.
- [ ] 3.3 Add unit tests for selection, hashing, target validation, mutual exclusion, state transitions, and retry lineage.
- [ ] 3.4 Add Testcontainers integration tests for migration execution, recovery, partial failure, stale sources, changed targets, and schema-plan regression.
- [ ] 3.5 Run `graphify update .` after implementation.
