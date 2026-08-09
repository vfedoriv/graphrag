## Context

The bounded route `GET /api/v1/documents/{documentId}/chunks/page` currently normalizes the request kind to `PARENT` or `CHILD` and passes nullable string filters to `DocumentChunkRepository.findPageByDocumentId`. Omitting `parentChunkId` means no parent predicate, so `kind=CHILD` cannot select only unparented children in a mixed document. The hierarchy service separately calls `countFlatChunksByDocumentId`, whose current predicate counts every unparented non-parent chunk, including legacy null or unsupported kinds.

The frontend contract already emits `kind=FLAT`. The backend must add a read-only virtual selector without changing the storage model, retrieval eligibility, graph processing, or complete-list compatibility route.

## Goals / Non-Goals

**Goals:**

- Make `kind=FLAT` valid for bounded chunk pages.
- Select only persisted `CHILD` chunks with `parentChunkId IS NULL`.
- Keep page totals and hierarchy `flatChunkCount` based on the same logical predicate.
- Preserve case normalization, ownership checks, page bounds, section filtering, deterministic ordering, and all existing valid filters.
- Document the selector and cover pure-flat, mixed, paging, ordering, validation, and contract behavior.

**Non-Goals:**

- Add `FLAT` to the persisted `ChunkKind` enum or write `FLAT` into Neo4j.
- Change chunk construction, processing, embeddings, lexical indexing, extraction, parent-context expansion, or cleanup.
- Migrate legacy null or unsupported kinds as part of this change.
- Change the complete-list chunk route or require a frontend request change.
- Guarantee a multi-request snapshot across hierarchy and page calls.

## Decisions

### Model `FLAT` as a request/read filter

Normalize the incoming selector into a request-level read-filter representation that can distinguish persisted `PARENT`, persisted `CHILD`, and virtual `FLAT`. The repository-facing operation must carry explicit flat intent—through a dedicated flat-page query or an equivalent explicit predicate/flag—rather than treating `FLAT` as a storage kind. This prevents the virtual value from leaking into persistence or response mapping.

An ordinary `CHILD` request continues to mean all persisted children, regardless of whether `parentChunkId` is set. A `FLAT` request translates to `kind = 'CHILD'` plus `parentChunkId IS NULL`.

### Reuse one canonical flat predicate for counts and pages

The flat page value query and its count query will both constrain the document, persisted child kind, null parent, and optional section index before ordering, skipping, limiting, or calculating totals. `countFlatChunksByDocumentId` will use the same persisted-child/null-parent definition. The page remains ordered by `chunkIndex ASC, id ASC`.

This is preferred over using `kind=CHILD` with an omitted parent filter because omission cannot represent `IS NULL`. It also avoids broadening the persisted enum, which would affect unrelated processing and indexing contracts.

### Reject contradictory filters before document or graph access

The service will reject a nonblank `parentChunkId` when the normalized selector is `FLAT`, using the stable message `parentChunkId cannot be used with kind=FLAT`. Existing validation for blank filters, page bounds, section bounds, `PARENT` plus parent id, and unsupported kinds remains in the same pre-access validation path. `flat` and surrounding whitespace are normalized using the existing case-insensitive behavior.

### Treat legacy unsupported kinds as an operational precondition

The canonical flat predicate deliberately excludes null and unsupported persisted kinds. Existing records that do not satisfy `PARENT` or `CHILD` must be deleted or reprocessed by the operational cleanup process before this contract is relied upon. No historical `FLAT` migration is introduced.

## Risks / Trade-offs

- [Duplicated Neo4j value/count predicates can drift] → Keep the flat predicate textually equivalent and add integration assertions for totals, pages, section filters, mixed populations, and stable ties.
- [Legacy null or unsupported kinds disappear from flat counts and pages] → Make the cleanup/reprocessing assumption explicit in the deployment notes and verify canonical kinds before rollout.
- [Hierarchy count and page total are separate requests] → Define the invariant for a stable document snapshot; expose existing revision metadata and leave concurrent-overwrite coordination unchanged.
- [Adding a virtual selector expands the public filter contract] → Update OpenAPI descriptions and retain the canonical response `kind=CHILD` so consumers do not infer a new persisted kind.

## Migration Plan

1. Add the read-filter normalization, contradiction validation, flat repository page/count predicates, and contract tests.
2. Publish the updated OpenAPI description and backend specification.
3. Deploy the backend; the existing frontend `kind=FLAT` request becomes valid without request coordination.
4. Delete or reprocess legacy null/unsupported chunk populations through the existing operational mechanisms.
5. Roll back by removing virtual `FLAT` support if needed; no persisted data or schema rollback is required.

## Open Questions

None. The cleanup or reprocessing mechanism for legacy chunk records remains an operational concern and is not expanded by this backend contract change.
