## 1. API And Configuration

- [x] 1.1 Add request/response DTOs for hybrid search, ranked chunk hits, source metadata, graph entities, and graph relationships
- [x] 1.2 Add bounded hybrid search configuration defaults for `topK`, candidate count, graph depth, and chunk text inclusion
- [x] 1.3 Add `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/hybrid-search` to `QueryController`

## 2. Retrieval Implementation

- [x] 2.1 Add `HybridSearchService` that resolves the configured `EmbeddingClient` and embeds the query text
- [x] 2.2 Query the existing `document_chunk_embedding` Neo4j vector index for candidate `DocumentChunk` hits
- [x] 2.3 Filter vector hits to `DocumentUpload` nodes in the requested knowledge base
- [x] 2.4 Expand graph context from matching chunks through `MENTIONS` and bounded neighboring relationships
- [x] 2.5 Return deterministic evidence-first results ordered by vector score
- [x] 2.6 Update architecture boundary tests or allowlists for any new direct `Neo4jClient` usage

## 3. Validation And Error Handling

- [x] 3.1 Validate required query text and enforce configured `topK` and `graphDepth` bounds
- [x] 3.2 Return an empty hit list when no chunks match the requested knowledge base
- [x] 3.3 Return a clear configuration error when no embedding provider is available

## 4. Tests And Documentation

- [x] 4.1 Add controller tests for request validation and service delegation
- [x] 4.2 Add service tests for embedding resolution, request bounds, empty results, and response mapping
- [x] 4.3 Add Neo4j integration coverage for vector retrieval, knowledge-base filtering, and graph expansion
- [x] 4.4 Update README/API documentation to include the hybrid search endpoint and response shape
- [x] 4.5 Run the focused hybrid search tests
- [x] 4.6 Run `./mvnw test`
- [x] 4.7 Run `graphify update .` after code changes
