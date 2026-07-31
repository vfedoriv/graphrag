## Context

The existing hybrid-search endpoint returns ranked chunk hits. Once children reference persisted parents, the system can enrich a precise child candidate with coherent context without implementing the broader agentic advanced-search plan.

This is proposal 5 of 6 and requires `add-parent-child-chunk-hierarchy`.

## Goals / Non-Goals

**Goals:**

- Preserve child retrieval/ranking/citation identity while loading bounded parent context.
- Validate hierarchy scope and revision before expansion.
- Bound synthesis context and expose content-safe diagnostics.
- Keep behavior useful to current hybrid search and ask orchestration.

**Non-Goals:**

- Add retriever branches, fusion algorithms, semantic rerankers, or query planning.
- Embed or directly retrieve parents.
- Replace child citations with parent citations for text retrieval.

## Decisions

### Expand after child candidate ranking

Dense and lexical retrieval continue to return child IDs and scores. Enrichment batches parent loads by ID, validates KB/document/run/revision scope, and attaches a bounded context object. Candidate ordering and channel diagnostics remain child-derived.

### Separate retrieval evidence from expanded context

Responses distinguish `retrievalChunkId/sourceText/sourceSpan` from `contextChunkId/contextText/contextRange`. Ask synthesis may consume the context text, but text claims cite the child. Graph claims continue to cite extraction parents under the hierarchy proposal.

### Apply deterministic budgets

Expansion enforces configured maximum parents, per-document contribution, total context tokens, and evidence count before synthesis. Duplicate children may share one loaded parent without duplicating context. Diversity continues to count by document.

### Constrain adjacency fallback

If a valid parent is unavailable for a legacy flat chunk, adjacent expansion may operate only within the same document and compatible structural section/revision. For hierarchical chunks it must remain within the same parent; cross-page adjacency is allowed only within an accepted cross-page parent.

### Keep logs content-free

Metrics and traces record candidate counts, expanded-parent counts, budget drops, strategy revision, validation failures, and timings. Text remains controlled by `AiObservationService`.

## Risks / Trade-offs

- [Expansion overwhelms answer budget] → Apply budgets before synthesis and deduplicate shared parents.
- [Stale or malicious parent IDs cross scope] → Validate all ownership and revision dimensions in the graph query and service boundary.
- [Parent context causes citation drift] → Keep evidence and context fields separate and validate returned citation kind.
- [Legacy chunks have weaker context] → Use constrained adjacency and report the fallback.

## Migration Plan

1. Add response fields as backward-compatible optional data.
2. Add batch parent loading and scope validation.
3. Enable bounded expansion through typed runtime query settings.
4. Update ask assembly and citation-integrity tests.
5. Roll back by disabling expansion; child retrieval remains unchanged.

## Open Questions

None.
