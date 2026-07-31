## Context

Recursive children are precise retrieval units but can be too small for entity resolution and relationship extraction. This change introduces persisted medium parent context while deliberately leaving search expansion for the next proposal.

This is proposal 4 of 6 and requires `adopt-recursive-token-aware-chunking`.

## Goals / Non-Goals

**Goals:**

- Persist coherent parents and contained children as one revision-scoped hierarchy.
- Keep children as the only embedded and lexically indexed kind.
- Run graph extraction on persisted parent context with authoritative parent evidence.
- Preserve cleanup, overwrite, retry, KB isolation, and citation integrity.

**Non-Goals:**

- Parent embeddings or direct parent retrieval.
- Parent expansion in search/ask flows.
- Derived summaries, propositions, or hypothetical questions.

## Decisions

### Materialize parent text from source spans

Parents and children are constructed from the same tracked parser ranges. Each parent stores complete bounded source text and hash; it is never reconstructed by concatenating overlapping child strings. Children store `parentChunkId` and containment is checked before persistence.

### Permit narrowly bounded cross-page PDF parents

A PDF parent may cover at most two consecutive pages when compatible structural continuity is explicit and target-token/page limits are satisfied. Its children remain page-bounded and ordered. Ambiguous continuity stops parent construction at the page boundary.

### Persist parents without vectors or retrieval index labels

Parents are `DocumentChunk` nodes with kind `PARENT`, but have no embedding and are excluded from dense and lexical indexes. Repository queries that mean “embedded chunks” or “retrieval chunks” filter to `CHILD`.

### Extract graph facts from parents

`GraphExtractionStage` selects persisted parents as extraction units. Graph evidence stores the parent ID, source range, structure, pages, run, and strategy revision. Public graph-fact citations expose that parent only; they never infer which child contains a fact.

### Treat hierarchy replacement as one ownership boundary

Parent and child persistence is atomic where supported or idempotent with checkpoints. Cleanup deletes evidence and relationships before all document-scoped parents and children. A successful run cannot leave an orphan child or a mixed-revision hierarchy.

## Risks / Trade-offs

- [Parent text duplicates child-covered source] → Accept one bounded copy and measure storage; avoid reconstruction complexity.
- [Cross-page parents combine unrelated content] → Require continuity, consecutive pages, and a two-page maximum.
- [Graph extraction output changes] → Add parent-sized evaluation fixtures and preserve fixed/flat rollback modes.
- [Partial writes create orphans] → Validate hierarchy pre-write and add post-write integrity checks plus cleanup recovery.

## Migration Plan

1. Add kind/parent properties and hierarchy-aware readers while flat children remain supported.
2. Add parent construction and persistence behind a revisioned strategy.
3. Switch graph extraction to parents and validate evidence APIs.
4. Update cleanup/replacement paths and integrity tests.
5. Roll back processing to flat recursive chunks; existing hierarchy remains safely readable/deletable.

## Open Questions

None.
