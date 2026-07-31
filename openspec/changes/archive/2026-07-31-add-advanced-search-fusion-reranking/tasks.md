## 1. Candidate Fusion

- [x] 1.1 Define evidence candidates and typed channel contributions without hybrid DTO coupling.
- [x] 1.2 Implement deterministic deduplication and normalized weighted RRF with `k=60`.
- [x] 1.3 Implement fusion-pool, graph-derived candidate, and per-branch bounds with diagnostics.

## 2. Expansion and Reranking

- [x] 2.1 Replace advanced-path `MENTIONS` traversal with bounded `HAS_GRAPH_EVIDENCE` and `ASSERTS_*` expansion.
- [x] 2.2 Adapt parent-context expansion and evidence assembly to the common candidate model while preserving citation kinds.
- [x] 2.3 Implement bounded structured model reranking and deterministic fused-rank fallback.
- [x] 2.4 Implement final evidence count and per-document diversity selection.

## 3. Verification

- [x] 3.1 Unit-test RRF determinism, query-count normalization, deduplication, diversity, and reranker fallback.
- [x] 3.2 Integration-test graph/parent expansion, missing-parent fallback, source bounds, and absence of `MENTIONS` contributions.
- [x] 3.3 Add versioned retrieval fixtures comparing mixed-query Recall@10 and semantic-only regression.
- [x] 3.4 Run focused tests and `graphify update .`.
