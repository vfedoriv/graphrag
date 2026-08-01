## Why

The only document-chunk read downloads the complete hierarchy and offers no direct chunk lookup, making large-document inspection and citation deep links inefficient. Clients need bounded, hierarchy-aware reads without losing the deterministic full-list behavior used by existing integrations.

## What Changes

- Add direct document-owned chunk lookup at `GET /api/v1/documents/{documentId}/chunks/{chunkId}`.
- Add a paginated chunk collection supporting filters for chunk kind, parent identifier, and section while preserving deterministic hierarchy/document ordering.
- Add a hierarchy-summary representation that returns parent metadata and child counts without returning all child text.
- Use ownership-safe `404` behavior for missing, foreign-document, or mismatched chunk identifiers.
- Preserve the existing complete-list route behavior during a documented compatibility window and expose the paginated contract without silently changing existing response envelopes.
- Add repository, controller, ordering, filtering, paging, ownership, direct-lookup, and OpenAPI tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities
- `document-management`: Adds bounded hierarchy-aware collection reads, hierarchy summaries, and direct owned chunk lookup while retaining the legacy complete-list contract temporarily.

## Impact

This affects document controllers and DTOs, document-processing read services, Neo4j chunk repository queries, OpenAPI documentation, and focused integration tests. Chunk persistence, processing, embeddings, extraction, and cleanup behavior are unchanged.
