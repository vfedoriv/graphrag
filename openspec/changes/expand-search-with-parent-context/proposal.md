## Why

After parent-child chunks exist, returning only precise child hits leaves synthesis without the coherent context already persisted for that purpose. Search should preserve child-level ranking and citation identity while loading a bounded, scope-validated parent excerpt for downstream use.

## What Changes

- Keep dense and lexical candidates keyed by embedded/indexed `CHILD` chunk IDs.
- Expand eligible child hits to their persisted parent after validating knowledge base, document, processing run, strategy revision, and parent-child scope.
- Preserve child score, channel diagnostics, source span, and citation identity separately from expanded context identity and text.
- Bound expansion by parent count, per-document diversity, evidence count, and total token budget.
- Keep adjacent-child expansion only as a constrained fallback within a compatible parent or structural section.
- Add response and observation diagnostics that report whether parent or adjacent expansion contributed without logging content.
- Add retrieval and citation-integrity evaluation cases, including page-bounded child evidence reached through cross-page parents.

## Capabilities

### New Capabilities

- `parent-context-expansion`: Scope-safe, budgeted small-to-big expansion that retains precise retrieval evidence.

### Modified Capabilities

- `hybrid-search`: Adds optional parent context to child hits while preserving ranking, KB isolation, and authoritative child source fields.
- `query-ask-orchestration`: Uses bounded expanded context for synthesis while validating and returning the correct child-versus-parent citation kind.
- `privacy-safe-operational-logging`: Adds content-free expansion diagnostics and strategy revision metadata.

## Impact

This affects hybrid-search candidate enrichment, query/ask evidence assembly, response DTOs, graph queries, runtime query budgets, observability, and retrieval integration tests. It does not add a new retriever, embed parents, implement semantic reranking, or deliver the broader agentic advanced-search plan.
