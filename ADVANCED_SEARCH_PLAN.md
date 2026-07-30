# Advanced Agentic Search

## Summary

Replace the existing synchronous `/queries/hybrid-search` endpoint with a durable, knowledge-base-scoped advanced-search workflow that returns a synthesized answer, claim-level citations, ranked chunk evidence, supporting graph facts, and retrieval diagnostics.

The workflow will be application-orchestrated rather than an open-ended LLM tool loop:

```text
Plan query
    |
    +-- Dense vector retrieval ------+
    +-- Lexical/full-text retrieval -+--> RRF fusion
    +-- Structured graph retrieval --+        |
    +-- Document metadata lookup ----+        v
                                      graph/context expansion
                                                |
                                             rerank
                                                |
                                    sufficiency evaluation
                                      | sufficient | gaps
                                      |            +-- one follow-up round
                                      v
                              cited answer synthesis
```

Defaults:

- Search the current knowledge base only; do not use web or cross-KB retrieval.
- Enforce a 60-second overall deadline.
- Allow at most three initial subqueries and one follow-up round.
- Execute as PostgreSQL-backed asynchronous runs with polling and cancellation.
- Retain completed run artifacts for 30 configurable days.
- Remove the existing `/hybrid-search` contract and DTOs as a breaking API change.

## Retrieval and Agent Design

### Planning

- Use the active KB AI profile and schema to produce a structured `AdvancedSearchPlan`, never executable model-generated Cypher.
- Include normalized subquestions, exact identifiers and phrases, optional document metadata constraints, and typed graph requests in the plan.
- Validate all generated labels, relationship types, properties, operators, limits, and literals against the active schema.
- Snapshot the schema ID/content hash, AI profile ID/revision, embedding space, prompt revisions, settings, and deadline on the run.

### Parallel retrievers

- **Dense vector retriever:** Extract the existing vector-query logic into a
  reusable retriever. Document child vectors use the globally enabled,
  versioned contextual `embeddingText` policy for TXT, PDF, and DOCX; query
  text is embedded as the query itself. Batch-embed all subqueries, use the
  isolated KB/embedding-space index, and return 30 candidates per subquery.
- **Lexical retriever:** Use Neo4j full-text indexes over unprefixed child
  `sourceText`. Contextual embedding headers never enter lexical text.
  Full-text and lexical search are the same retrieval family; exact phrase and
  identifier searches become boosted query variants rather than separate
  infrastructure.
  - Create one deterministic KB-scoped label and full-text index per KB.
  - Use the language-neutral `standard-no-stop-words` analyzer, synchronous consistency, escaped Lucene input, and bounded phrase/term queries.
  - Assign the lexical label during chunk persistence. On first advanced search, idempotently label existing chunks, create the index, and wait for it to become online without reprocessing documents.
- **Structured graph retriever:** Compile a validated graph-plan IR into parameterized Cypher supporting schema-safe node filters, up to two typed relationship hops, projections, ordering, and bounded aggregations.
  - Every canonical fact must be supported through `GraphExtractionEvidence.knowledgeBaseId`.
  - Node and relationship results must resolve through `sourceChunkId` to the
    persisted extraction parent. Public graph-fact results cite that parent
    only, not inferred child spans.
  - Do not reuse unrestricted `/queries/ask` Cypher generation as an agent tool.
- **Metadata retriever:** Query PostgreSQL for explicit filename/content-type matches. Exact document matches contribute bounded document chunks and can constrain follow-up planning.

A failed optional retriever does not fail the run when enough evidence remains. Embedding failure permits lexical/graph continuation; graph failure permits text retrieval continuation.

### Fusion, expansion, and reranking

- Normalize all results into `EvidenceCandidate`, keyed by chunk ID, with graph facts attached through evidence records.
- Fuse ranks using weighted reciprocal-rank fusion with `k=60`, equal initial branch weights, and normalization by each branch's executed subquery count. Never compare raw vector and full-text scores directly.
- Retain per-channel raw rank and score for diagnostics, but use only fused and reranked scores for final ordering.
- Keep the top 60 fused candidates.
- Expand the best ten seed chunks through the current provenance model:
  `DocumentChunk -> HAS_GRAPH_EVIDENCE -> ASSERTS_* -> canonical facts`.
  Remove the obsolete `MENTIONS` traversal.
- Add bounded related-fact evidence and context expansion. Prefer the retrieval
  child's explicit persisted parent and load its bounded stored text after
  validating knowledge-base, document, parent-child, and strategy-revision
  scope. A PDF parent may span consecutive pages only when it was materialized
  under the accepted structural-continuity, token, and page-span bounds. Retain
  adjacent chunks (`chunkIndex +/- 1`) as a fallback within the same parent or
  compatible structural section. A missing or invalid parent falls back to the
  precise child. The expanded parent is synthesis context. Text-retrieval
  citations remain tied to precise page-bounded child/source spans; graph-fact
  citations identify the persisted extraction parent and its bounded page
  range. Cap graph depth at two and graph-derived candidates at 30.
- Rerank the top 20 candidates with the existing profile chat model using bounded excerpts and structured relevance output. Fall back deterministically to fused rank if parsing, timeout, or provider failure occurs.
- Select ten final evidence items by default, maximum 20, with a default cap of three chunks per document unless the plan identifies a comparison requiring broader document coverage.

### Adaptation and answer synthesis

- Run a structured sufficiency check after reranking for subquestion coverage, contradictions, and missing evidence.
- Permit one follow-up round with at most two refined queries only when the evaluator reports a concrete gap, the run is not cancelled, and enough deadline remains.
- Synthesize a structured result containing answer text, confidence enum, limitations, claims, and citations.
- Require each substantive claim to cite known citation IDs. Graph-based claims
  cite the extraction-parent citation and additionally identify graph
  fact/evidence IDs; they do not synthesize child citations.
- Validate citations against the final evidence set. Allow one bounded repair call; if still invalid, return a partial result with evidence and an explicit answer-unavailable or insufficient-evidence outcome.
- Treat retrieved document text as untrusted data and delimit it from system instructions to reduce prompt-injection risk.

## APIs, Persistence, and Configuration

### Public API

- `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs`
  - Request: `query` (required, maximum 8,000 characters), optional `maxEvidence` (`1..20`), and optional `includeEvidenceText`.
  - Return `202 Accepted` with run ID, status, stage, timestamps, and polling routes.
  - Return `429` before creating a run when admission capacity is exhausted.
- `GET /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs`
  - Return paginated newest-first run history with an optional status filter.
- `GET /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs/{runId}`
  - Return run status, current stage, stage counters, cancellation state, timestamps, and sanitized failure details.
- `GET /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs/{runId}/result`
  - Return completed or partial results; return RFC 7807 `409` while unavailable and `404` after retention cleanup.
- `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs/{runId}/cancel`
  - Immediately cancel queued work and cooperatively cancel running branch futures. Repeated cancellation is idempotent.

Run statuses:

- `QUEUED`
- `RUNNING`
- `COMPLETED`
- `PARTIAL`
- `FAILED`
- `CANCELLED`
- `INTERRUPTED`

Run stages:

- `PLANNING`
- `RETRIEVING`
- `FUSING`
- `EXPANDING`
- `RERANKING`
- `EVALUATING`
- `FOLLOW_UP`
- `SYNTHESIZING`

Progress uses stages and counters rather than an inaccurate percentage.

Result types include:

- `AdvancedSearchAnswer` with text, confidence, limitations, and claims.
- `AdvancedSearchClaim` with citation references.
- `AdvancedSearchEvidence` with citation ID, final rank, fused/rerank scores,
  precise retrieval-child and optional expanded-parent identities, child page
  metadata, optional bounded parent page range/excerpt, contributing
  retrievers, and graph facts.
- `AdvancedSearchGraphFact` with fact kind, canonical fact ID, schema
  representation, source evidence IDs, and extraction-parent chunk IDs and
  citations.
- `AdvancedSearchDiagnostics` with plan summary, rounds, branch statuses, candidate counts, latency, fallback use, and token usage.

Raw prompts, model responses, secrets, and executable Cypher must never be exposed.

### PostgreSQL persistence

Add Flyway-managed tables:

- `advanced_search_run`: ownership, input query, status/stage, cancellation flag, profile/schema/settings snapshots, worker claim, deadline, timestamps, expiry, failure category, and optimistic version.
- `advanced_search_attempt`: round, subquery index, retriever, status, candidate count, latency, failure category, and content-free diagnostics.
- `advanced_search_result`: answer, claims, evidence snapshots, graph derivations, and final diagnostics as validated JSONB.

Store only bounded cited excerpts, not duplicate full chunks. Cascade run artifacts when their KB is deleted. A scheduled batch cleanup removes terminal runs after the default 30-day TTL. On application startup, stale `QUEUED` or `RUNNING` rows become `INTERRUPTED`; model calls are not automatically replayed.

### Execution and settings

- Use dedicated bounded executors: two concurrent runs, queue capacity 50, and four branch tasks per run.
- Use atomic PostgreSQL worker claims and check cancellation/deadline between every stage. Ignore late results after cancellation.
- Give LLM and Neo4j calls the smaller of their configured timeout and the run's remaining deadline.
- Add typed `app.advanced-search.*` settings:
  - Live: deadline 60 seconds, max subqueries 3, follow-up rounds 1, candidates 30, fusion pool 60, rerank pool 20, default/max evidence 10/20, graph depth 2, graph rows 50, evidence-text default, query-length limit, and retention days 30.
  - Restart-required/deployment-managed: run concurrency, queue capacity, branch concurrency, and full-text analyzer.
- Snapshot effective settings per run.
- Continue privacy-safe operational logging: identifiers, counts, timings, statuses, and fingerprints only.
- Add an `advanced-search` workflow observation with child observations and metrics for retriever latency/failures, candidate overlap, rerank movement, follow-up use, citation counts, abstention, cancellation, deadline exhaustion, and model tokens.
- Migrate compatible persisted hybrid-setting overrides to advanced equivalents, then remove legacy settings. Retire candidate-multiplier and default-graph-depth overrides because the new pipeline has no equivalent semantics.

## Implementation Sequence

1. Create an OpenSpec change covering the new `advanced-search` capability and updates to runtime settings, AI observability, graph-data-plane isolation, and removal of the old hybrid-search contract.
2. Extract reusable vector retrieval and implement the KB-scoped lexical index/retriever, including existing-chunk backfill and index-readiness handling.
3. Implement the graph-plan IR, schema validator/renderer, evidence-based graph retrieval, provenance expansion, candidate model, RRF fusion, and document diversity rules.
4. Add PostgreSQL run/attempt/result persistence, state transitions, worker admission, cancellation, recovery, TTL cleanup, and the new controller endpoints.
5. Add planner, reranker, sufficiency evaluator, answer synthesizer, citation validator, deterministic fallbacks, observability, and typed runtime settings.
6. Remove `HybridSearchService`, its endpoint/DTOs, `MENTIONS`-based behavior, and obsolete settings. Update README/OpenAPI and synchronized contributor documentation.
7. Benchmark in staging against the current vector-only behavior, warm lexical indexes, and then enable the breaking endpoint replacement.
8. Run `graphify update .` after implementation.

## Test and Acceptance Plan

- Unit-test structured plan validation, Lucene escaping, graph rendering,
  KB/schema scope injection, RRF determinism, deduplication, diversity, bounded
  stored-parent and adjacency expansion, parent/child scope and revision
  validation, cross-page-parent constraints, missing-parent fallback, reranker
  fallback, global contextual-header separation from lexical/citation text,
  sufficiency gates, citation validation, deadlines, cancellation, and state
  transitions.
- Neo4j integration-test vector and lexical ranking, per-KB candidate isolation
  with stronger competing hits from another KB, index backfill/readiness,
  evidence-based graph expansion without `MENTIONS`, graph-fact
  extraction-parent citation resolution, absence of inferred child citations,
  multi-hop limits, and stale artifact cleanup.
- PostgreSQL integration-test migrations, claims, optimistic updates, run/result ownership, paging, cancellation races, startup interruption recovery, TTL cleanup, and KB cascade deletion.
- Controller-test all response contracts and RFC 7807 errors, including `202`, `409`, `429`, invalid bounds, missing runs, and idempotent cancellation.
- Add deterministic end-to-end scenarios for semantic paraphrases, exact identifiers, relationship questions, multi-document comparisons, contradictions, no evidence, one retriever timeout, invalid model output, citation repair, and cancellation.
- Build a versioned evaluation fixture set and compare against vector-only retrieval:
  - 100% KB isolation and citation referential integrity.
  - At least a 10 percentage-point Recall@10 improvement on mixed lexical/semantic/graph questions.
  - No more than two percentage points of regression on the semantic-only subset.
  - Every emitted substantive claim has a fixture-verifiable supporting citation.
  - Balanced runs meet the 60-second configured deadline under the selected provider envelope.
  - Optional retriever failure yields `PARTIAL` rather than losing usable evidence.

## Assumptions

- Version one searches only the selected KB and uses the existing active chat and embedding models.
- Version one embeds and retrieves child chunks only. Stored parents are
  expanded after a child/graph seed and have no vectors or vector-index label;
  parent and derived retrieval embeddings require a separate evaluated change.
- Chunk migration is initiated explicitly through the knowledge-base
  `/reprocessing-plans` resource with reason `CHUNK_STRATEGY_MIGRATION`,
  normally selecting `OUTDATED_STRATEGY`. Advanced-search diagnostics report
  mixed strategy revisions until that durable plan completes; settings updates
  never reprocess the corpus implicitly.
- Spring AI remains the model/client abstraction; application services own parallelism and agent state. Do not introduce a generic tool-calling loop or second orchestration framework.
- PostgreSQL remains authoritative for runs and operational state; Neo4j remains authoritative for chunks, vectors, facts, and provenance.
- External search engines, web retrieval, community/global GraphRAG summaries, entity embeddings, HyDE, dedicated cross-encoder models, and unrestricted model-generated Cypher are deferred.
- The existing generic `/queries/ask` API remains separate; advanced search does not depend on its currently weaker KB-scope guarantees.
