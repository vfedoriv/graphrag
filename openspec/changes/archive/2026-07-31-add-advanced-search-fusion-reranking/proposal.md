## Why

Independent retrievers improve recall but do not produce a coherent evidence order because their raw scores are incomparable. Advanced search needs deterministic fusion, scope-safe expansion, bounded semantic reranking, and document diversity before adaptive planning or answer generation is added.

## What Changes

- Normalize dense, lexical, metadata, and graph outputs into chunk-keyed evidence candidates.
- Apply weighted reciprocal-rank fusion with `k=60`, per-branch subquery normalization, deduplication, and a bounded fusion pool.
- Expand leading seeds through current evidence provenance and existing parent-context rules, removing `MENTIONS` from this path.
- Rerank a bounded pool with structured model output and deterministic fused-rank fallback.
- Select a bounded, document-diverse final evidence set while retaining channel and fallback diagnostics.

## Capabilities

### New Capabilities

- `advanced-search-ranking`: Cross-retriever candidate fusion, provenance/context expansion, reranking, and diverse evidence selection.

### Modified Capabilities

None.

## Impact

This adds the common evidence candidate model and ranking pipeline, adapts existing parent expansion/evidence assembly, uses the active profile chat model for reranking, and adds deterministic ranking tests. It depends on proposals 1 and 2 but does not add durable runs, follow-up planning, or answer synthesis.
