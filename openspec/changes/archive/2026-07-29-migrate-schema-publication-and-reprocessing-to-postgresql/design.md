## Context

Evaluation, publication, and post-activation reprocessing consume immutable draft revisions and coordinate model calls, schema creation, activation, and document overwrite processing. Their current Neo4j records include durable results, claims, retries, plans, items, counters, and navigation summaries.

## Goals / Non-Goals

**Goals:**

- Complete PostgreSQL ownership of schema-draft operational state.
- Preserve evaluation durability, revision-specific publication, and bounded reprocessing.
- Use conditional claims and recoverable lifecycle checkpoints.

**Non-Goals:**

- Changing evaluation metrics, readiness policy, API shapes, or overwrite semantics.
- Migrating historical Neo4j data.
- Performing graph work inside relational transactions.

## Decisions

1. **Separate histories by responsibility.** Evaluation runs own durable outcomes. Publications reference the exact aggregate revision and resulting inactive schema. Reprocessing plans own bounded per-document items and derived counters.

2. **Enforce revision and reuse identity in PostgreSQL.** Unique constraints prevent duplicate evaluation reuse, duplicate publication of the same revision identity, and duplicate plan items. Enum checks and optimistic versions guard state transitions.

3. **Use conditional worker claims.** Claim updates require expected status, ownership, retry eligibility, and version. Claims carry bounded expiry/recovery metadata and use status/claim/retry indexes.

4. **Keep cross-store orchestration phase-separated.** Evaluation commits its run before model work. Publication commits readiness/intent before schema creation and completion afterward. Reprocessing commits an item claim before invoking document overwrite and commits the result afterward.

5. **Derive counters safely.** Plan counters are updated in relational transactions and can be repaired from item state during recovery rather than treated as an independent source of truth.

## Risks / Trade-offs

- [A published schema is created but publication completion fails] → Publication identity is unique and recovery resolves the existing inactive schema idempotently.
- [A reprocessing claim expires during long processing] → Completion requires ownership; recovery observes underlying document-run state before retrying.
- [Counters drift after interruption] → Recompute from item rows during recovery and test invariant repair.
- [Evaluation payload sizes grow in text columns] → Preserve current representation now; revisit JSONB separately with explicit fingerprint migration.

## Migration Plan

1. Add evaluation, publication, plan, and item tables and indexes.
2. Add JPA entities, adapters, conditional claim queries, and recovery projections.
3. Migrate evaluation and durable outcome reuse.
4. Migrate publication readiness and inactive-schema creation.
5. Migrate reprocessing planning, worker execution, counters, retries, and recovery.
6. Complete navigation summaries and focused/full-flow tests.

Rollback assumes empty target stores. Any claimed work must reach a terminal or recovered state before reverting.

## Open Questions

- None.
