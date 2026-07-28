## ADDED Requirements

### Requirement: Embedding-space operations do not require operational graph nodes
The system SHALL select, create, validate, and retire knowledge-base embedding indexes using copied chunk scope and relational profile metadata without matching a Neo4j knowledge-base or AI-profile node.

#### Scenario: A knowledge base receives its first chunks
- **WHEN** processing persists chunks for a compatible embedding space
- **THEN** the scoped vector index is created or reused idempotently
- **AND** its membership is determined from chunk scope

#### Scenario: Profile compatibility is checked
- **WHEN** a profile assignment or processing operation validates existing embeddings
- **THEN** the check uses relational profile metadata and scoped chunk/index state
