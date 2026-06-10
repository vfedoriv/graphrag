## ADDED Requirements

### Requirement: Active schema resolver returns complete schema context
The system SHALL provide a shared active schema resolver that resolves a knowledge base identifier into the active schema definition and parsed schema document required by downstream workflows.

#### Scenario: Active schema exists
- **WHEN** a workflow resolves active schema context for a knowledge base with an active schema definition
- **THEN** the resolver returns the knowledge base identifier, schema definition identifier, schema definition metadata, and parsed `SchemaDocument`

### Requirement: Active schema resolver preserves missing-resource behavior
The resolver SHALL preserve the existing failure behavior for unknown knowledge bases, missing active schema references, and missing schema definitions.

#### Scenario: Knowledge base does not exist
- **WHEN** a workflow resolves active schema context for an unknown knowledge base
- **THEN** the resolver fails with the same not-found behavior used by existing query and extraction services

#### Scenario: Knowledge base has no active schema
- **WHEN** a workflow resolves active schema context for a knowledge base without an active schema identifier
- **THEN** the resolver fails with the same no-active-schema behavior used by existing query and extraction services

#### Scenario: Active schema definition is missing
- **WHEN** a workflow resolves active schema context and the active schema identifier does not point to a schema definition
- **THEN** the resolver fails with the same schema-not-found behavior used by existing query and extraction services

### Requirement: Query and extraction workflows use shared resolution
Graph extraction, Cypher generation, and Cypher validation workflows SHALL obtain active schema context through the shared resolver rather than duplicating repository lookup and schema parsing logic.

#### Scenario: Workflows require active schema context
- **WHEN** graph extraction, Cypher generation, or Cypher validation needs an active schema
- **THEN** the workflow delegates active schema lookup and parsing to the shared resolver
