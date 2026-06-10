## Why

Document processing already persists chunk embeddings and extracted graph provenance, but query workflows do not use those embeddings during retrieval. Adding a hybrid search endpoint lets clients retrieve semantically relevant document chunks and graph context before introducing answer synthesis.

## What Changes

- Add a new knowledge-base-scoped hybrid search endpoint that embeds a user query and searches the existing `DocumentChunk.embedding` vector index.
- Filter vector hits to documents in the requested knowledge base.
- Expand from matching chunks through graph provenance, including mentioned extracted entities and nearby relationships.
- Return ranked evidence with vector scores, source document/chunk metadata, optional chunk text, and graph context.
- Keep the initial capability deterministic and evidence-first; LLM answer synthesis is out of scope for this change.

## Capabilities

### New Capabilities
- `hybrid-search`: Defines vector-plus-graph retrieval over persisted document chunks and extracted graph context.

### Modified Capabilities

## Impact

- Affected API: new `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/hybrid-search` endpoint.
- Affected services: new hybrid search orchestration using the existing embedding client and Neo4j vector index.
- Affected DTOs: request and response models for ranked hybrid search hits and graph context.
- Affected tests: controller, service, and Neo4j integration coverage for vector retrieval, knowledge-base filtering, and graph expansion.
- Runtime behavior: endpoint requires an embedding provider profile, similar to document processing.
