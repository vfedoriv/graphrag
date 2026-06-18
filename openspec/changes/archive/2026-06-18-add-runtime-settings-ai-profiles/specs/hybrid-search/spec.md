## MODIFIED Requirements

### Requirement: Search query is embedded for vector retrieval
The system SHALL embed the submitted search query using the requested knowledge base active AI profile before querying stored document chunk embeddings.

#### Scenario: Embedding provider is configured
- **WHEN** hybrid search receives a valid request and the requested knowledge base has an active AI profile with compatible embedding settings
- **THEN** the system embeds the search query with that profile
- **AND** the system uses the resulting vector for retrieval

#### Scenario: Embedding provider is missing
- **WHEN** hybrid search receives a valid request and the requested knowledge base has no resolvable active embedding provider
- **THEN** the system fails the request with a clear configuration error

#### Scenario: Active profile is incompatible with stored embeddings
- **WHEN** hybrid search receives a valid request and the requested knowledge base active profile is incompatible with stored chunk embedding dimensions or model metadata
- **THEN** the system rejects the request before executing vector retrieval
- **AND** the response identifies the configuration compatibility problem
