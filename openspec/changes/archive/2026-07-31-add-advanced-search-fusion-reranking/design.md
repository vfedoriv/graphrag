## Context

Dense, lexical, metadata, and graph branches have different score distributions and may execute different numbers of subqueries. Existing parent-context and graph-evidence assembly already encode important child-versus-parent citation distinctions that ranking must preserve.

This is proposal 3 of 7 and requires `add-advanced-search-text-retrieval` and `add-advanced-search-graph-retrieval`.

## Goals / Non-Goals

**Goals:**

- Produce deterministic cross-channel ordering without comparing raw scores.
- Preserve source/citation identity through deduplication, expansion, and reranking.
- Bound model context, graph expansion, evidence count, and per-document dominance.

**Non-Goals:**

- Plan subqueries, decide sufficiency, perform follow-up rounds, or synthesize answers.
- Introduce a dedicated cross-encoder.
- Retrieve or embed parent chunks directly.

## Decisions

### Key candidates by precise child identity

All branches normalize to `EvidenceCandidate` keyed by chunk ID. Graph facts attach through evidence records; parent context remains synthesis-only. Each contribution keeps branch, subquery, raw rank, and raw score diagnostics.

### Use normalized weighted RRF

Fusion uses `sum(weight / (60 + rank))`, divided per branch by its executed subquery count. Equal initial branch weights avoid encoding unevaluated preferences. Raw scores remain diagnostic only. The fused pool is capped at 60.

### Expand only leading seeds through authoritative provenance

The best ten seeds expand through `HAS_GRAPH_EVIDENCE` and `ASSERTS_*`, never `MENTIONS`. Existing parent scope/revision validation and bounded adjacency fallback are reused. Graph-derived candidates and traversal depth have separate caps.

### Make model reranking optional

The active profile chat model reranks at most 20 candidates from bounded, delimited excerpts using a structured relevance response. Invalid output, timeout, or provider failure preserves deterministic fused order.

### Apply diversity after reranking

Final selection defaults to ten and caps at twenty, with three chunks per document unless an explicit comparison policy later raises that bound.

## Risks / Trade-offs

- [RRF defaults are poorly calibrated] → Preserve diagnostics and evaluate versioned fixtures before changing weights.
- [Reranking adds latency and variance] → Bound pool/excerpts and retain fused fallback.
- [Expansion changes citation identity] → Keep precise child, graph parent, and context-only parent as distinct typed fields.

## Migration Plan

1. Add candidate normalization and deterministic fusion.
2. Adapt evidence/context expansion without changing current hybrid behavior.
3. Add structured reranking and fallback.
4. Add diversity selection and evaluation fixtures.

## Open Questions

None.
