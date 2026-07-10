## MODIFIED Requirements

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
