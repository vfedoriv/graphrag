# embedding-space-management Specification

## Purpose

Define stable embedding-space identities, isolated vector retrieval, and safe migration of legacy chunk embeddings.

## Requirements

### Requirement: Embedding spaces have stable non-secret identities
The system SHALL derive and persist an embedding-space identity from normalized embedding provider endpoint, embedding model, and embedding dimensions without including API key material.

#### Scenario: Profiles use different providers with equal dimensions
- **WHEN** two profiles use equal embedding dimensions but different normalized provider endpoints or embedding models
- **THEN** the system assigns different embedding-space identities

#### Scenario: API key is rotated
- **WHEN** an API key changes while provider endpoint, embedding model, and dimensions remain unchanged
- **THEN** the system preserves the embedding-space identity

### Requirement: Vector retrieval is isolated by knowledge base and embedding space
The system SHALL query a vector index that contains only chunks from the requested knowledge base and compatible embedding space.

#### Scenario: Other knowledge bases have more similar global candidates
- **WHEN** chunks from other knowledge bases would otherwise consume vector-index candidates
- **THEN** hybrid search still evaluates the requested knowledge base's isolated vector index
- **AND** it can return its available top-ranked compatible chunks

### Requirement: Legacy chunks are classified or quarantined
The system SHALL backfill a legacy chunk into an embedding space only when its knowledge base profile unambiguously matches its stored model and dimensions.

#### Scenario: Legacy chunk is unambiguous
- **WHEN** a legacy chunk's stored model and dimensions match its knowledge base active profile
- **THEN** migration assigns the corresponding embedding-space identity and index membership

#### Scenario: Legacy chunk is ambiguous
- **WHEN** a legacy chunk cannot be assigned to one compatible embedding space
- **THEN** the system excludes it from vector retrieval
- **AND** reports that the knowledge base requires explicit re-embedding

### Requirement: Embedding-space operations do not require operational graph nodes
The system SHALL select, create, validate, and retire knowledge-base embedding indexes using copied chunk scope and relational profile metadata without matching a Neo4j knowledge-base or AI-profile node.

#### Scenario: A knowledge base receives its first chunks
- **WHEN** processing persists chunks for a compatible embedding space
- **THEN** the scoped vector index is created or reused idempotently
- **AND** its membership is determined from chunk scope

#### Scenario: Profile compatibility is checked
- **WHEN** a profile assignment or processing operation validates existing embeddings
- **THEN** the check uses relational profile metadata and scoped chunk/index state

### Requirement: Tokenizer-aware embedding compatibility
The system SHALL include resolved tokenizer identity in the embedding-processing compatibility snapshot and SHALL reject a knowledge-base profile change that would make existing embedded chunks incompatible under the configured embedding-space policy.

#### Scenario: Compatible tokenizer metadata update
- **WHEN** a profile revision resolves to the same embedding model, dimensions, embedding-space identity, and tokenizer identity as existing chunks
- **THEN** the compatibility guard may accept the update

#### Scenario: Incompatible tokenizer change with chunks
- **WHEN** a profile update would change tokenizer identity for a knowledge base that already has embedded chunks
- **THEN** the update is rejected and the prior active profile remains unchanged
