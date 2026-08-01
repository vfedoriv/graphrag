## Context

Chunking settings are mutated through the generic runtime-settings API, which repeats one global effective revision and migration lifecycle on every setting row. Chunk migration creation already computes a target revision, validates schema/profile/embedding state, classifies outdated documents, snapshots immutable targets, and enforces `expectedChunkerRevision`, but none of that selection is available without creating a durable plan. Reprocessing history is shared by schema activation and chunk migration but can be filtered only by draft. Retry uses a boolean whose `false` state is structurally accepted and unconditionally rejected.

## Goals / Non-Goals

**Goals:**

- Publish one authoritative effective chunking aggregate for frontend summaries.
- Preview migration readiness and exact selection without creating operational state.
- Reuse one classification policy for preview and plan creation.
- Filter mixed reprocessing history before pagination and totals.
- Make retry policy closed and evolvable while preserving the one valid legacy request during migration.

**Non-Goals:**

- Automatically reprocess documents after a setting change.
- Mutate settings through the new read model.
- Remove existing runtime-setting chunk metadata immediately.
- Relax active-schema, active-profile, embedding-space, revision, or destructive-plan exclusion guards.

## Decisions

### Introduce an aggregate chunking-state resource

Add `GET /api/v1/chunking-state`. Its typed response contains canonical effective values, their sources, strategy revision, resolved tokenizer policy/count mode, parser-policy revision, representation revision, settings hash, effective chunker revision, migration lifecycle, and compatibility aliases with canonical-key precedence.

The service composes existing typed accessors and revision calculators; it does not re-read arbitrary strings in controllers. Runtime settings remain the only mutation API. Existing per-setting revision/lifecycle fields remain during a compatibility window, and clients refetch the aggregate after a successful setting update instead of changing the bulk-update response envelope.

### Use a side-effect-free POST for structured migration preview

Add `POST /api/v1/knowledge-bases/{knowledgeBaseId}/chunk-migrations/preview?page=&size=` with a body containing `selection`, optional `documentIds`, and optional processing options. POST is used because `DOCUMENT_IDS` and processing options are structured and may exceed practical query-string bounds; the operation remains read-only.

The response includes the current chunking-state identity, active schema/profile/embedding target, `ready`, stable blockers, whole-knowledge-base classification counts (`noChunks`, `outdated`, `current`), selected count, and a deterministic page of selected document summaries. Preview does not reserve execution capacity or write a plan, item, run, chunk, or graph artifact.

### Share target resolution and document classification

Extract a read-only migration target/classifier used by preview and creation. It resolves document-specific parser/options context and classifies against the effective document target, rather than comparing only the global revision. Selection validation and ownership behavior remain identical for `OUTDATED_STRATEGY`, `DOCUMENT_IDS`, and `ALL`.

Plan creation continues to accept `expectedChunkerRevision`; the preview returns this value. Creation recomputes the target and returns `409` if it changed, so preview is informative rather than a lock or reservation.

Stable blocker codes cover missing active schema, unresolvable profile, incompatible embedding space, invalid migration target, and another active destructive plan. Invalid selection shapes remain `400`; foreign document IDs remain ownership-safe `404`.

### Apply optional plan filters in one repository query

Extend the list API with optional enum parameters `reason`, `selection`, and `status`, combinable with `draftId`. A repository specification/query applies all non-null filters before count and page selection, then orders by `createdAt DESC, id DESC`. Selection matching treats null selection as distinct; no client-side post-filtering is permitted.

### Replace the retry boolean with a closed mode

The canonical body becomes `{ "mode": "RESNAPSHOT_UNRESOLVED" }`. The service switches exhaustively on the enum and retains prior successes while resnapshotting unresolved documents under the current target. During a compatibility window, deprecated `{ "resnapshotUnresolvedDocuments": true }` maps to the enum; `false`, unknown modes, and conflicting fields are rejected as `400`.

An enum was chosen over a bodyless endpoint because future policies may need explicit semantics. Keeping the boolean indefinitely was rejected because it continues to advertise an impossible state.

## Risks / Trade-offs

- [Preview becomes stale before confirmation] → Return `expectedChunkerRevision` and revalidate every creation target and blocker transactionally.
- [Preview classification is expensive for large knowledge bases] → Batch relational/Neo4j reads, return bounded document pages, and test query counts and limits.
- [Preview and creation drift] → Share target resolution, selection validation, and classification code rather than duplicating controller logic.
- [Removing repeated runtime metadata breaks clients] → Retain it through a documented compatibility window and make the aggregate additive.
- [Legacy retry clients send the boolean] → Accept only the previously valid `true` form temporarily and mark it deprecated in OpenAPI.

## Migration Plan

1. Extract and test the shared chunk migration target/classification service.
2. Add the global state and knowledge-base preview resources.
3. Add repository-backed history filters.
4. Add the retry enum and temporary legacy request adapter.
5. Update OpenAPI and frontend guidance to refetch chunking state after settings mutations.
6. Remove the legacy retry boolean and repeated per-setting aggregate metadata only in later explicit compatibility changes.

## Open Questions

None.
