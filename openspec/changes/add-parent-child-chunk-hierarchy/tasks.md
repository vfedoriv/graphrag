## 1. Hierarchy Construction

- [ ] 1.1 Add parent/child chunk kinds, hierarchy settings, containment validation, and parent revision/hash/count fields.
- [ ] 1.2 Build parent and child slices from shared tracked source ranges and prove parent text is not reconstructed from overlapping children.
- [ ] 1.3 Implement structurally compatible, consecutive, at-most-two-page PDF parent assembly with page-bounded children.
- [ ] 1.4 Add unit tests for containment, ordering, mixed revisions, continuity rejection, and token/page bounds.

## 2. Persistence and Extraction

- [ ] 2.1 Persist complete parents and ordered children with atomic or checkpointed hierarchy integrity.
- [ ] 2.2 Restrict embeddings, vector indexes, lexical indexes, and embedded-chunk existence checks to children.
- [ ] 2.3 Change graph extraction to use persisted parents and store authoritative parent-scoped evidence.
- [ ] 2.4 Update public graph evidence and document chunk DTOs to expose correct parent/child citation and provenance fields.
- [ ] 2.5 Make overwrite, replacement, deletion, retry, and graph-artifact cleanup remove the complete hierarchy and dependencies.

## 3. Verification

- [ ] 3.1 Add Neo4j integration tests for hierarchy isolation, orphan prevention, index exclusion, and atomic lifecycle behavior.
- [ ] 3.2 Add graph extraction/citation fixtures for same-page and accepted cross-page parents.
- [ ] 3.3 Run focused unit tests and relevant Testcontainers processing, extraction, provenance, and cleanup integration tests.
- [ ] 3.4 Run `graphify update .` after implementation.
