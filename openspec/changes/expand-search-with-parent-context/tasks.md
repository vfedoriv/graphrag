## 1. Expansion Model and Queries

- [ ] 1.1 Add typed parent-expansion budgets and separate retrieval-evidence versus expanded-context response fields.
- [ ] 1.2 Add a batched parent loader that validates knowledge base, document, retained run, strategy revision, and membership in one scoped query.
- [ ] 1.3 Implement deterministic parent deduplication, total-token, parent-count, evidence-count, and per-document limits.
- [ ] 1.4 Implement constrained legacy adjacency fallback and same-parent hierarchical adjacency checks.

## 2. Search and Ask Integration

- [ ] 2.1 Enrich hybrid child hits after ranking without changing child scores or channel diagnostics.
- [ ] 2.2 Update ask context assembly to consume bounded parent context while retaining child text citations and graph-parent citations.
- [ ] 2.3 Add content-free expansion metrics, trace attributes, validation outcomes, and timings.
- [ ] 2.4 Preserve backward-compatible child-only behavior when expansion is disabled, invalid, or over budget.

## 3. Verification

- [ ] 3.1 Add unit tests for scope rejection, deduplication, budget ordering, legacy fallback, and citation-kind validation.
- [ ] 3.2 Add hybrid-search and ask integration fixtures including sibling hits and page-bounded children under cross-page parents.
- [ ] 3.3 Measure retrieval quality, expanded-context size, latency, duplicate evidence, and citation integrity against the flat baseline.
- [ ] 3.4 Run focused unit tests and relevant Testcontainers hybrid-search/query integration tests.
- [ ] 3.5 Run `graphify update .` after implementation.
