## 1. Embedding-Space Model

- [x] 1.1 Implement stable, non-secret embedding-space identity derivation and persist it on profile-resolved chunk writes.
- [x] 1.2 Implement a single embedding-space compatibility policy for profile assignment, profile mutation, processing, and hybrid search.
- [x] 1.3 Add deterministic KB-and-space vector-index naming, safe label/index lifecycle, and index observability.

## 2. Runtime Integration

- [x] 2.1 Update document processing to select and validate the target embedding space before writing chunks.
- [x] 2.2 Update hybrid search to query the matching isolated index and honor effective graph-depth bounds.
- [x] 2.3 Block incompatible shared-profile updates atomically and return affected knowledge bases in conflicts.

## 3. Migration and Verification

- [x] 3.1 Backfill unambiguous legacy chunk embedding-space metadata and quarantine ambiguous chunks for re-embedding.
- [x] 3.2 Add tests for profile updates, profile reassignment, differing dimensions, equal-dimension differing providers, and query isolation.
- [x] 3.3 Add migration/index lifecycle tests, then run focused processing and hybrid-search integration tests plus the full Maven suite.
