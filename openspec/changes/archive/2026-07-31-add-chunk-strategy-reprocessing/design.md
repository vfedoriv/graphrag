## Context

Runtime chunking settings intentionally affect only subsequent processing. The existing PostgreSQL schema-reprocessing plan already provides durable plan/item state, progress, recovery, and linked retry, but its domain and request contract are schema-specific.

This is proposal 6 of 6. It requires the versioned chunking contracts and should be implemented after the target production chunker revision is available; parent-aware search is not a technical prerequisite.

## Goals / Non-Goals

**Goals:**

- Reuse one knowledge-base reprocessing resource and durable worker framework.
- Select outdated, explicit, or all owned documents with an immutable target snapshot.
- Prevent concurrent destructive plans and mixed targets.
- Preserve partial commits, recovery, status, history, and linked retry.

**Non-Goals:**

- Start migration automatically from a settings update.
- Choose final chunk-size defaults or trigger a production migration.
- Create chunk-only orchestration tables or endpoints.
- Retire schema-specific navigation behavior.

## Decisions

### Generalize reason and target, not the endpoint

The existing endpoint accepts a discriminated request with `SCHEMA_ACTIVATION` and `CHUNK_STRATEGY_MIGRATION` reasons. Reason-specific target/selection validation produces one common plan/item lifecycle. Existing schema requests remain backward compatible.

Alternative: create a separate chunk migration controller and tables. Rejected because it duplicates destructive-plan locking, recovery, retries, and progress semantics.

### Snapshot the complete processing target atomically

Plan creation resolves and canonically serializes chunk settings/revisions, tokenizer/header/parser policy, AI profile and embedding space, active schema, processing options, selected document IDs, and content hashes. Workers receive this snapshot rather than reading later live settings.

### Use explicit selection modes

`OUTDATED_STRATEGY` selects documents with no chunks or a different persisted effective revision. `DOCUMENT_IDS` requires a non-empty owned set. `ALL` forces a rebuild. The request’s `expectedChunkerRevision` is an optimistic guard against a stale operator view.

### Block changing targets without undoing completed items

A changed document hash yields `STALE_SOURCE`. A changed chunk/profile/embedding/schema target yields `BLOCKED_TARGET_CHANGED` for queued work. Completed documents remain independently committed. Retry creates a linked plan after explicitly resnapshotting unresolved documents.

### Enforce one destructive plan per knowledge base

Plan creation takes a PostgreSQL-backed conflict guard covering queued/running schema and chunk plans. A conflict or stale target returns RFC 7807 `409 Conflict`.

## Risks / Trade-offs

- [Generalization destabilizes schema publication] → Preserve reason-specific adapters and run all existing schema-plan tests unchanged.
- [Snapshots become large] → Store canonical bounded JSON plus indexed identity/hash columns needed for queries.
- [Target changes mid-run produce partial migration] → Expose partial progress and require an explicit linked retry.
- [Concurrent workers process one item twice] → Retain transactional claim/checkpoint semantics and idempotent overwrite.

## Migration Plan

1. Add nullable reason/target/selection/snapshot columns and backfill existing plans as schema plans.
2. Adapt existing schema behavior to the generalized internal contracts.
3. Add chunk selection and immutable processing inputs.
4. Add mutual exclusion, new outcomes, recovery, and retry tests.
5. Expose effective revision/migration metadata in settings.
6. Roll back API enablement while retaining generalized rows readable by the prior schema adapter where possible.

## Open Questions

None.
