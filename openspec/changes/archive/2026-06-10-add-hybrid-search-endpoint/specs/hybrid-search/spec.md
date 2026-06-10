## ADDED Requirements

### Requirement: Hybrid search endpoint is exposed
The system SHALL expose a knowledge-base-scoped endpoint for hybrid vector and graph search.

#### Scenario: Hybrid search request is submitted
- **WHEN** a client submits `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/hybrid-search` with a valid search query
- **THEN** the system returns a hybrid search response for that knowledge base

### Requirement: Search query is embedded for vector retrieval
The system SHALL embed the submitted search query using the configured embedding provider before querying stored document chunk embeddings.

#### Scenario: Embedding provider is configured
- **WHEN** hybrid search receives a valid request and an embedding provider is available
- **THEN** the system embeds the search query and uses the resulting vector for retrieval

#### Scenario: Embedding provider is missing
- **WHEN** hybrid search receives a valid request and no embedding provider is available
- **THEN** the system fails the request with a clear configuration error

### Requirement: Vector hits are scoped to the knowledge base
The system SHALL return only document chunks that belong to documents in the requested knowledge base.

#### Scenario: Vector index returns chunks from multiple knowledge bases
- **WHEN** vector retrieval finds chunks across multiple knowledge bases
- **THEN** the response includes only chunks connected to `DocumentUpload` nodes for the requested knowledge base

### Requirement: Hybrid search returns ranked chunk evidence
The system SHALL return ranked chunk hits with vector similarity scores and source metadata.

#### Scenario: Matching chunks are found
- **WHEN** hybrid search finds matching document chunks
- **THEN** each result includes the chunk id, document id, chunk index, vector score, metadata, and chunk text according to the request settings

#### Scenario: No matching chunks are found
- **WHEN** hybrid search finds no chunks for the requested knowledge base
- **THEN** the response contains an empty hit list rather than failing

### Requirement: Hybrid search includes graph context
The system SHALL expand graph context from matching chunks through existing graph provenance relationships.

#### Scenario: Matching chunk mentions extracted entities
- **WHEN** a matching chunk has `MENTIONS` relationships to extracted graph entities
- **THEN** the corresponding search hit includes those entities and bounded neighboring relationships

#### Scenario: Graph expansion limit is provided
- **WHEN** a hybrid search request provides a graph expansion depth
- **THEN** the system respects the configured maximum depth and does not return unbounded graph traversal results

### Requirement: Hybrid search request bounds are enforced
The system SHALL enforce configured limits for result count and graph expansion depth.

#### Scenario: Request exceeds configured limits
- **WHEN** a client requests more results or deeper graph expansion than allowed
- **THEN** the system rejects or clamps the request according to the endpoint contract and never executes an unbounded search
