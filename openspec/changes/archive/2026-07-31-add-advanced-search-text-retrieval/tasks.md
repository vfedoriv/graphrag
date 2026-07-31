## 1. Retrieval Contracts

- [x] 1.1 Define branch request/result contracts with child source identity, subquery rank, raw score, deadline, and sanitized diagnostics.
- [x] 1.2 Extract current vector-query behavior into a reusable dense retriever while preserving the synchronous hybrid-search caller.
- [x] 1.3 Add batched query embedding and KB/embedding-space candidate isolation tests.

## 2. Lexical and Metadata Branches

- [x] 2.1 Add deterministic KB lexical label/index naming and child persistence labeling.
- [x] 2.2 Implement idempotent legacy-child backfill, full-text index creation, and bounded readiness waiting.
- [x] 2.3 Implement Lucene escaping and bounded boosted phrase/term query compilation over `sourceText`.
- [x] 2.4 Implement scoped relational filename/content-type matching and bounded document chunk lookup.

## 3. Verification

- [x] 3.1 Unit-test query escaping, branch bounds, contextual-header separation, and failure diagnostics.
- [x] 3.2 Neo4j integration-test vector/full-text ranking, legacy backfill, readiness, and competing cross-KB hits.
- [x] 3.3 Run focused tests and `graphify update .`.
