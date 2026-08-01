## Context

`GET /api/v1/documents/{documentId}/chunks` verifies the relational document, loads every document-scoped chunk from Neo4j, maps full text and provenance, and returns one list. The route is useful for compatibility but scales poorly and cannot serve a citation deep link without downloading the whole hierarchy. Existing chunk identity, parent identity, kind, section, source/page range, and deterministic `chunkIndex` provide enough structure for bounded reads.

## Goals / Non-Goals

**Goals:**

- Add direct owned chunk lookup.
- Add bounded filtered pages with exact totals and stable ordering.
- Summarize parent hierarchy without transferring child text.
- Preserve ownership-safe behavior and the current complete-list route.

**Non-Goals:**

- Change chunk persistence, hierarchy construction, embedding, extraction, or cleanup.
- Add full-text chunk search or arbitrary Neo4j query parameters.
- Remove or silently change the existing list response envelope.

## Decisions

### Add explicit subresources rather than overloading the legacy route

Keep `GET /documents/{documentId}/chunks` unchanged. Add:

- `GET /documents/{documentId}/chunks/page`
- `GET /documents/{documentId}/chunks/hierarchy`
- `GET /documents/{documentId}/chunks/{chunkId}`

Static subresources are declared alongside direct lookup and produce distinct OpenAPI response types. This avoids changing the legacy route from a list into a page conditionally based on query parameters.

### Use bounded typed filters and one canonical order

The page resource accepts optional `kind`, `parentChunkId`, and `sectionIndex`, plus zero-based `page` and bounded `size`. Unsupported combinations or bounds return `400`. Queries apply filters in Neo4j before count and page selection.

All collection reads order by persisted `chunkIndex ASC, id ASC`. Children selected by `parentChunkId` additionally expose their existing `childIndex`; the canonical document order remains authoritative. The page uses the existing typed `PageResponse` envelope.

### Resolve relational ownership before graph lookup

Every route first requires the document in PostgreSQL, then queries a chunk by both `chunkId` and `documentId`. A chunk that exists under another document is indistinguishable from a missing chunk and returns `404`. This prevents the global Neo4j chunk identifier from becoming an ownership bypass.

### Return metadata-only hierarchy summaries

The hierarchy endpoint pages `PARENT` chunks and returns identity, order, provenance, token estimate, revision metadata, structural scope, and child count but no parent or child text. It supplies links or identifiers clients can use to page children through `parentChunkId` or fetch a specific chunk.

Parents are the summary root. Flat/legacy documents with no parent chunks return an empty parent page and explicit flat chunk count so clients can fall back to the paginated collection.

### Preserve the full-list route through explicit deprecation governance

The old route remains behaviorally unchanged and is documented as compatibility-only after clients adopt bounded reads. Its eventual removal requires a separate breaking OpenSpec change and usage review; this proposal sets no silent sunset.

## Risks / Trade-offs

- [Neo4j paging totals require an additional count query] → Keep filters indexed/scoped by document and test bounded query behavior on large fixtures.
- [Concurrent overwrite changes pages between requests] → Treat each request as a current snapshot and expose processing/effective revision metadata so clients can detect target changes.
- [Chunk IDs leak cross-document existence] → Always constrain direct lookup by the already-owned document and return uniform `404`.
- [Two collection contracts increase API surface temporarily] → Clearly mark the complete list as compatibility-only and make the page route the documented default.

## Migration Plan

1. Add scoped Neo4j direct, count, page, and parent-summary repository operations.
2. Add DTOs/services/controllers with ownership and validation tests.
3. Publish OpenAPI examples and update consumer guidance to prefer bounded routes.
4. Retain the legacy list until a separately approved removal change.
5. Roll back by removing only the new read routes; persistence is unchanged.

## Open Questions

None.
