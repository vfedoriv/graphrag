## 1. Hierarchy Construction

- [x] 1.1 Add parent/child chunk kinds, hierarchy settings, containment validation, and parent revision/hash/count fields.
- [x] 1.2 Build parent and child slices from shared tracked source ranges and prove parent text is not reconstructed from overlapping children.
- [x] 1.3 Implement structurally compatible, consecutive, at-most-two-page PDF parent assembly with page-bounded children.
- [x] 1.4 Add unit tests for containment, ordering, mixed revisions, continuity rejection, and token/page bounds.

## 2. Persistence and Extraction

- [x] 2.1 Persist complete parents and ordered children with atomic or checkpointed hierarchy integrity.
- [x] 2.2 Restrict embeddings, vector indexes, lexical indexes, and embedded-chunk existence checks to children.
- [x] 2.3 Change graph extraction to use persisted parents and store authoritative parent-scoped evidence.
- [x] 2.4 Update public graph evidence and document chunk DTOs to expose correct parent/child citation and provenance fields.
- [x] 2.5 Make overwrite, replacement, deletion, retry, and graph-artifact cleanup remove the complete hierarchy and dependencies.

## 3. Verification

- [x] 3.1 Add Neo4j integration tests for hierarchy isolation, orphan prevention, index exclusion, and atomic lifecycle behavior.
- [x] 3.2 Add graph extraction/citation fixtures for same-page and accepted cross-page parents.
- [x] 3.3 Run focused unit tests and relevant Testcontainers processing, extraction, provenance, and cleanup integration tests.
- [x] 3.4 Run `graphify update .` after implementation.
