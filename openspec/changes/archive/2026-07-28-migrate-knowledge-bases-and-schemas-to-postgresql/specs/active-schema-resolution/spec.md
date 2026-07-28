## ADDED Requirements

### Requirement: Active schema resolution uses relational associations
The system SHALL resolve a knowledge base's active schema and complete stored content from PostgreSQL without requiring operational schema or knowledge-base nodes in Neo4j.

#### Scenario: A workflow requests active schema context
- **WHEN** the knowledge base has one active relational association
- **THEN** the resolver returns the same identity and parsed schema content expected by extraction and query workflows

#### Scenario: No active association exists
- **WHEN** a workflow requests an active schema for a knowledge base without one
- **THEN** the existing not-found or precondition behavior is preserved
