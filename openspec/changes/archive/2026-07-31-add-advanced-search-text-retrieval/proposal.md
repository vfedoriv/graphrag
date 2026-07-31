## Why

The current hybrid search couples one vector query, response assembly, and graph enrichment in a synchronous service. Advanced search needs reusable, independently testable text retrieval branches that preserve knowledge-base isolation and work even when one provider is unavailable.

## What Changes

- Extract child-chunk dense retrieval behind a reusable branch contract and batch-embed bounded subqueries.
- Add knowledge-base-scoped Neo4j full-text retrieval over unprefixed child `sourceText`, including lazy existing-chunk labeling and index readiness.
- Add PostgreSQL filename and content-type metadata retrieval.
- Return bounded branch results with raw rank/score and source identity, without fusing or synthesizing them.

## Capabilities

### New Capabilities

- `advanced-search-text-retrieval`: Isolated dense, lexical, and document-metadata retrieval branches for advanced search.

### Modified Capabilities

None.

## Impact

This affects chunk persistence labels, Neo4j vector/full-text index management, the existing hybrid vector query, document metadata repositories, retrieval DTOs, and focused Neo4j integration tests. It does not add graph-plan retrieval, cross-branch ranking, public run APIs, or LLM answer generation.
