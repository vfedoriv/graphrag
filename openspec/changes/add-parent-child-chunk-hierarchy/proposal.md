## Why

Small retrieval chunks improve precision but can omit the context needed for entity resolution and graph extraction. Persisted, bounded parents provide coherent extraction and synthesis context without weakening the exact child spans used by text retrieval and citations.

## What Changes

- Materialize bounded `PARENT` chunks and ordered page-bounded `CHILD` chunks from the same tracked source spans.
- Persist complete parent source text, content hash, child count, structural/page range, chunker revision, and child-to-parent references.
- Allow a PDF parent to span at most two consecutive pages only when structural continuity and token bounds are satisfied; children and text citations remain page-bounded.
- Exclude parents from dense and lexical indexes and do not generate parent embeddings.
- Run graph extraction on the persisted parent context and link graph evidence to that authoritative extraction parent.
- Make hierarchy writes, overwrite, retry, replacement, deletion, and graph-artifact cleanup atomic or safely recoverable so successful state cannot contain orphan children.
- Extend document chunk reads to distinguish parent and child nodes while preserving ownership and revision checks.

## Capabilities

### New Capabilities

- `parent-child-chunk-hierarchy`: Parent construction, child containment, cross-page bounds, persistence, traversal, indexing exclusions, and hierarchy integrity.

### Modified Capabilities

- `graph-extraction-result-contract`: Graph facts and evidence use the persisted extraction parent as their authoritative source chunk.
- `multi-source-graph-provenance`: Public graph evidence exposes the parent source range and never invents child citations for parent-derived facts.
- `graph-artifact-cleanup`: Replacement and deletion remove complete parent-child hierarchies and all dependent evidence.
- `document-management`: Chunk reads expose chunk kind, hierarchy identity, and bounded provenance.
- `graph-data-plane-isolation`: Parent/child storage and traversal retain KB, document, run, and strategy-revision isolation.

## Impact

This affects chunk construction and persistence, Neo4j properties and indexes, graph extraction orchestration, evidence mapping, document chunk DTOs, cleanup paths, and Testcontainers integration tests. Search remains child-seeded and does not expand parent context until the follow-up retrieval change.
