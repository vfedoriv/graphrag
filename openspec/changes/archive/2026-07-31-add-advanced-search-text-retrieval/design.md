## Context

`HybridSearchService` currently owns profile resolution, query embedding, vector index preparation, Cypher execution, metadata enrichment, context expansion, and response assembly. The advanced workflow needs dense, lexical, and metadata branches that can execute separately and later participate in bounded orchestration.

This is proposal 1 of 7 and has no dependency on the other advanced-search proposals.

## Goals / Non-Goals

**Goals:**

- Reuse current embedding-space isolation and contextual embedding policy.
- Add exact-term and phrase recall without contaminating lexical text with contextual headers.
- Make branch failures and diagnostics explicit and content-free.

**Non-Goals:**

- Fuse branch ranks, rerank candidates, or expose a new public endpoint.
- Search parents directly or change parent/context citation rules.
- Add another search engine.

## Decisions

### Use one common branch result contract

Each retriever returns child chunk/document identity, source bounds, branch rank, raw score, and content only when requested. The contract does not assign a cross-channel score. This prevents vector similarity and Lucene score from being compared directly.

### Keep dense retrieval profile and embedding-space scoped

Extract the existing vector query without changing its semantics: query text is embedded as itself, child vectors retain the versioned `embeddingText` policy, and the deterministic KB/space vector index limits the candidate budget before ranking.

### Use lazy KB-scoped Neo4j full-text indexes

Persist a deterministic lexical label on new child chunks. On first use, idempotently label legacy children, create the corresponding index with `standard-no-stop-words` and synchronous consistency, and wait within the caller deadline for `ONLINE`. Lucene text is escaped and phrase/term variants are bounded.

Alternative: one global full-text index filtered after retrieval. Rejected because stronger cross-KB hits could consume the candidate budget.

### Keep metadata lookup relational

Filename and content-type filters query authoritative PostgreSQL document rows and return bounded owned document IDs. Chunk loading remains a separate scoped operation.

## Risks / Trade-offs

- [Many KB-specific indexes increase Neo4j schema count] → Use deterministic names, lazy creation, cleanup with KB deletion, and operational diagnostics.
- [First search pays backfill/index warm-up latency] → Make readiness bounded and observable; fail only the lexical branch when another branch can continue.
- [Lucene syntax becomes an injection surface] → Escape all input and compile only bounded phrase/term variants.

## Migration Plan

1. Extract the vector retriever without changing the current hybrid endpoint behavior.
2. Assign lexical labels to newly persisted children and add lazy legacy backfill/index readiness.
3. Add metadata lookup and branch-level tests.
4. Roll back by disabling lexical branch use; existing vector behavior remains available.

## Open Questions

None.
