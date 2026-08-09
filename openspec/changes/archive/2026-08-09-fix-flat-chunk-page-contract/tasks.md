## 1. Read-filter contract and validation

- [x] 1.1 Introduce a request/read-filter representation that distinguishes virtual `FLAT` from persisted `PARENT` and `CHILD` without changing `ChunkKind`.
- [x] 1.2 Normalize `flat` case-insensitively and preserve existing page, size, blank-filter, section, and ownership validation behavior.
- [x] 1.3 Reject `kind=FLAT` with a concrete `parentChunkId` using the stable RFC 7807 explanation before document or graph access.

## 2. Neo4j page and count predicates

- [x] 2.1 Add the explicit flat page value/count predicate `documentId = requested document AND kind = 'CHILD' AND parentChunkId IS NULL`, including optional section filtering before paging and counting.
- [x] 2.2 Align `countFlatChunksByDocumentId` with the persisted-child/null-parent predicate and retain `chunkIndex ASC, id ASC` ordering for page reads.
- [x] 2.3 Preserve existing unfiltered, `PARENT`, `CHILD`, and concrete-parent page behavior and keep the complete-list route unchanged.

## 3. Public contract documentation

- [x] 3.1 Update `DocumentController` parameter and operation descriptions to document `PARENT`, `CHILD`, and virtual `FLAT` semantics and the contradictory-filter error.
- [x] 3.2 Ensure generated OpenAPI metadata describes returned flat records as persisted `CHILD` records rather than introducing a `FLAT` storage value.

## 4. Regression and integration coverage

- [x] 4.1 Add service tests for pure flat pages, case normalization, returned `CHILD` kind, mixed-population exclusion, section filtering, and contradictory `FLAT`/parent validation.
- [x] 4.2 Add Neo4j integration tests for multiple flat pages, mixed parents/parented children/unparented children, stable identifier ties, exact totals, section filters, and count/page equivalence.
- [x] 4.3 Add coverage proving null or unsupported legacy kinds are excluded from the canonical flat predicate while existing valid-kind behavior remains unchanged.
- [x] 4.4 Extend the OpenAPI contract tests for the virtual selector description and stable response schema.

## 5. Verification and rollout readiness

- [x] 5.1 Run focused document/chunk unit and integration tests, including the Testcontainers-backed repository suite.
- [x] 5.2 Verify the generated contract and document the operational cleanup/reprocessing precondition for legacy null or unsupported chunk kinds.
