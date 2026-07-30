## Why

Changing the default chunk strategy affects only future processing, leaving existing corpora on older revisions. Operators need an explicit, bounded, durable migration path that reuses the existing PostgreSQL reprocessing machinery without silently mixing live settings or competing destructive plans.

## What Changes

- Generalize the existing knowledge-base reprocessing-plan resource to accept a typed `CHUNK_STRATEGY_MIGRATION` reason alongside schema reprocessing.
- Add `OUTDATED_STRATEGY`, `DOCUMENT_IDS`, and explicit forced `ALL` selection modes.
- Require an optimistic `expectedChunkerRevision` target and atomically snapshot chunk settings, tokenizer/header/parser revisions, AI profile and embedding space, active schema, processing options, and selected document hashes.
- Select only owned documents and run existing overwrite processing from the immutable snapshot rather than later live settings.
- Mark changed binaries as `STALE_SOURCE` and queued work with a changed target as `BLOCKED_TARGET_CHANGED`; keep completed items independently committed.
- Reject stale targets and any concurrent destructive reprocessing plan for the same knowledge base with `409 Conflict`.
- Reuse existing status, history, item progress, recovery, and linked retry operations with explicit unresolved-document resnapshotting.
- Report the effective chunker revision and migration requirement through runtime settings; settings changes never create a plan automatically.

## Capabilities

### New Capabilities

- `chunk-strategy-reprocessing`: Typed selection, immutable target snapshots, execution, blocking, progress, recovery, and retry for chunk migrations.

### Modified Capabilities

- `schema-reprocessing-plans`: Generalizes the shared resource and mutual-exclusion rules while preserving schema-specific behavior.
- `runtime-application-settings`: Reports effective chunker revision and the explicit reprocessing lifecycle for older documents.
- `document-processing-options`: Allows migration workers to invoke overwrite processing with an immutable plan snapshot.

## Impact

This affects reprocessing request/response contracts, PostgreSQL plan and item entities/migrations, repositories, worker/recovery services, overwrite processing inputs, settings responses, conflict handling, and integration tests. It does not automatically migrate data, choose final chunk-size defaults, or retire the fixed strategy.
