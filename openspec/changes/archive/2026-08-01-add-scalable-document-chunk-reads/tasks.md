## 1. Scoped Chunk Read Persistence

- [x] 1.1 Add document-scoped Neo4j repository operations for direct chunk lookup, filtered counts/pages, parent-summary counts/pages, and flat chunk counts
- [x] 1.2 Enforce canonical `chunkIndex ASC, id ASC` ordering and apply kind, parent, and section filters before count and page selection
- [x] 1.3 Add repository integration fixtures covering hierarchical, flat, empty, cross-document, filtered, and large paginated chunk sets

## 2. Services and DTOs

- [x] 2.1 Define typed chunk-page and metadata-only hierarchy-summary DTOs using the standard page envelope
- [x] 2.2 Add service methods that verify relational document ownership before graph access and constrain direct lookup by both document and chunk identifiers
- [x] 2.3 Add validation for page/size bounds and supported kind, parent, and section filter combinations with RFC 7807 errors

## 3. Public Read Endpoints

- [x] 3.1 Add `GET /api/v1/documents/{documentId}/chunks/page` with bounded filters and deterministic page metadata
- [x] 3.2 Add `GET /api/v1/documents/{documentId}/chunks/hierarchy` returning parent summaries and flat chunk counts without chunk text
- [x] 3.3 Add `GET /api/v1/documents/{documentId}/chunks/{chunkId}` with uniform ownership-safe `404` behavior
- [x] 3.4 Preserve the existing complete-list route unchanged and document it as compatibility-only pending a separate removal decision

## 4. Contract and Integration Verification

- [x] 4.1 Add controller/OpenAPI tests for all new envelopes, filters, examples, validation errors, and direct deep links
- [x] 4.2 Add integration tests for ownership isolation, stable ordering/ties, exact filtered totals, empty pages, parent-child navigation, flat summaries, and legacy-route compatibility
- [x] 4.3 Run focused document/chunk unit and integration tests, the fast test profile, relevant Neo4j Testcontainers tests with escalation, and `graphify update .`
