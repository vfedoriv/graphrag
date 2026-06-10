## Context

Document processing already parses uploaded files, chunks text, embeds each chunk, stores `DocumentChunk.embedding`, creates the `document_chunk_embedding` Neo4j vector index, and links chunks to extracted graph entities with `MENTIONS`. The current query API only supports Cypher generation, validation, execution, and one-shot ask; it does not use stored embeddings for retrieval.

The natural integration point is a new query service that embeds the user's search text, retrieves matching chunks through the existing vector index, filters those hits to the requested knowledge base, and expands graph context from the matched chunks.

## Goals / Non-Goals

**Goals:**
- Expose a knowledge-base-scoped hybrid search endpoint that combines vector retrieval and graph provenance.
- Return ranked evidence with enough chunk and graph context for clients to inspect why a result matched.
- Reuse existing `EmbeddingClient`, `Neo4jClient`, chunk storage, vector index, and `MENTIONS` relationships.
- Keep search deterministic and testable before adding answer synthesis.

**Non-Goals:**
- Generate natural-language answers from retrieved context.
- Replace `/queries/ask`, `/queries/generate`, `/queries/validate`, or `/queries/execute`.
- Add a new vector store dependency or duplicate embeddings outside Neo4j.
- Reprocess existing documents or change chunk persistence.

## Decisions

- Add `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/hybrid-search`.
  - Rationale: this is a retrieval surface, not a Cypher execution surface, so a separate endpoint avoids changing `/queries/ask` semantics.
  - Alternative considered: extend `/queries/ask`; rejected because answer synthesis and retrieval evidence have different response contracts.

- Introduce a `HybridSearchService` that uses the existing `EmbeddingClient` for query embeddings and direct `Neo4jClient` Cypher for retrieval.
  - Rationale: query embedding must use the same configured embedding provider as document processing, and direct Cypher keeps the retrieval query explicit.
  - Alternative considered: use Spring AI Neo4j vector store; rejected because the project currently disables that auto-configuration and already owns the Neo4j data model.

- Use the existing `document_chunk_embedding` vector index and `db.index.vector.queryNodes(...)` for initial retrieval.
  - Rationale: the codebase already creates this index and has integration coverage proving the procedure works against the configured Neo4j version.
  - Implementation should filter returned chunks through `(:DocumentUpload {knowledgeBaseId})-[:HAS_CHUNK]->(:DocumentChunk)` and return results ordered by vector score.

- Expand graph context from each hit through `(:DocumentChunk)-[:MENTIONS]->(entity)` and bounded neighboring relationships.
  - Rationale: chunk-to-entity provenance already exists and gives a stable bridge from semantic text retrieval to the extracted knowledge graph.
  - Expansion depth should be request/config bounded to avoid accidental large traversals.

- Return evidence-first response objects.
  - Rationale: clients need ranked chunks, scores, source metadata, mentioned entities, and relationships before an LLM answer layer can be evaluated safely.

## Risks / Trade-offs

- Vector search followed by knowledge-base filtering may underfill results in databases with many knowledge bases -> Query more candidates than `topK` and cap the candidate multiplier in configuration.
- Graph expansion can become expensive on dense graphs -> Bound `graphDepth`, `topK`, and returned relationship counts.
- The default profile has no embedding provider -> Return a clear runtime error when no `EmbeddingClient` is available, matching existing document-processing behavior.
- Chunk text may contain sensitive content -> Make `includeChunkText` request-controlled and default it through configuration.
- Scores from vector search and graph context are different signals -> Keep ranking based on vector score in v1 and expose graph context separately instead of inventing a combined score.
