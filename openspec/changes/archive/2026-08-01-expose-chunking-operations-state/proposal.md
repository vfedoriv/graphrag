## Why

The backend can execute chunk-strategy migrations, but clients cannot inspect one authoritative effective chunking state, preview migration selection, or query migration-only history accurately before creating durable work. The current retry request also exposes a boolean state the service always rejects.

## What Changes

- Add a dedicated read-only chunking-state resource containing canonical effective settings, strategy/tokenizer/parser/representation revisions, effective chunker revision, migration lifecycle, and compatibility-alias precedence.
- Add a knowledge-base-scoped chunk-migration readiness/preview resource for `OUTDATED_STRATEGY`, `DOCUMENT_IDS`, and `ALL` selections without creating a plan.
- Report stable blocker codes, active schema/profile/embedding readiness, current target revision, counts for missing/outdated/current chunks, and a bounded paginated document preview.
- Require plan creation to continue using `expectedChunkerRevision`, allowing clients to bind confirmation to the previewed target and receive `409 Conflict` when it becomes stale.
- Add server-side reprocessing-history filters for `reason`, `selection`, and `status`, combinable with the existing owned `draftId` filter before pagination and totals are calculated.
- Replace the unsupported retry boolean with a closed retry mode whose initial value explicitly resnapshots unresolved documents.
- Keep runtime settings as the mutation API and preserve existing response fields during a compatibility window; return or expose the refreshed chunking-state representation after chunk-setting bulk updates where the public contract permits.

## Capabilities

### New Capabilities
- `chunking-operations-state`: Defines authoritative global chunking state plus knowledge-base migration readiness and selection previews.

### Modified Capabilities
- `chunk-strategy-reprocessing`: Adds preview-bound plan creation semantics and an explicit closed retry policy.
- `schema-reprocessing-plans`: Adds repository-backed combinable history filters with correct ownership, ordering, paging, and totals.
- `runtime-application-settings`: Establishes the dedicated chunking-state resource as the authoritative aggregate while retaining settings as the mutation mechanism.

## Impact

This affects runtime-settings and reprocessing controllers/DTOs, chunk revision assembly, migration selection logic, relational plan repository queries, retry validation, OpenAPI, and unit/integration tests. Existing unfiltered history and plan creation behavior remain compatible, and no preview operation creates plans, items, processing runs, or graph mutations.
