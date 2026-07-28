## Purpose

Define the knowledge-base-scoped hybrid search behavior that combines stored document chunk embeddings with bounded graph context.
## Requirements
### Requirement: Hybrid search endpoint is exposed
The system SHALL expose a knowledge-base-scoped endpoint for hybrid vector and graph search.

#### Scenario: Hybrid search request is submitted
- **WHEN** a client submits `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/hybrid-search` with a valid search query
- **THEN** the system returns a hybrid search response for that knowledge base

### Requirement: Search query is embedded for vector retrieval
The system SHALL embed the submitted search query using the requested knowledge base active AI profile and its compatible embedding space before querying stored document chunk embeddings.

#### Scenario: Embedding provider is configured
- **WHEN** hybrid search receives a valid request and the requested knowledge base has an active AI profile with a compatible embedding space
- **THEN** the system embeds the search query with that profile
- **AND** the system uses the resulting vector for retrieval from that knowledge base and embedding space

#### Scenario: Embedding provider is missing
- **WHEN** hybrid search receives a valid request and the requested knowledge base has no resolvable active embedding provider
- **THEN** the system fails the request with a clear configuration error

#### Scenario: Active profile is incompatible with stored embeddings
- **WHEN** hybrid search receives a valid request and the requested knowledge base active profile is incompatible with stored chunk embedding-space metadata
- **THEN** the system rejects the request before executing vector retrieval
- **AND** the response identifies the configuration compatibility problem

### Requirement: Vector hits are scoped to the knowledge base
The system SHALL return only document chunks that belong to documents in the requested knowledge base and compatible embedding space, using an index that is isolated from incompatible KB-space candidates.

#### Scenario: Other knowledge bases share an embedding provider
- **WHEN** vector retrieval is requested for a knowledge base whose provider is also used by other knowledge bases
- **THEN** the response includes only chunks from the requested knowledge base
- **AND** unrelated chunks do not consume its retrieval candidate budget

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

### Requirement: Hybrid search scopes candidates within Neo4j
The system SHALL restrict vector and graph candidates to the requested knowledge base and compatible embedding space before applying the candidate limit.

#### Scenario: Another knowledge base has closer competing vectors
- **WHEN** hybrid search runs for a knowledge base whose vectors compete with closer vectors from another knowledge base
- **THEN** only candidates in the requested knowledge base can consume its result limit

### Requirement: Hybrid search enriches metadata relationally in batches
The system SHALL batch-load PostgreSQL document metadata for Neo4j search hits and SHALL preserve response ordering and current API representations.

#### Scenario: Multiple hits reference documents
- **WHEN** Neo4j returns hits for multiple document IDs
- **THEN** document metadata is retrieved through a bounded batch operation rather than one query per hit
- **AND** the final results preserve their search ranking

#### Scenario: A stale graph hit references missing metadata
- **WHEN** a hit's document no longer exists in PostgreSQL
- **THEN** the hit is omitted or handled through the established stale-artifact policy
- **AND** metadata from another document is never substituted
