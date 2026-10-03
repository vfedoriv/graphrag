# active-schema-resolution Specification

## Purpose
TBD - created by archiving change introduce-active-schema-resolver. Update Purpose after archive.
## Requirements
### Requirement: Active schema resolver returns complete schema context
The system SHALL provide shared active schema resolution that returns an immutable snapshot of the knowledge base identifier, schema definition identifier, definition metadata, exact stored content and content hash, and parsed schema document required by downstream workflows. The snapshot SHALL NOT expose persistence records, repositories, provider clients, or secrets; its nested schema collections SHALL be immutable.

#### Scenario: Active schema exists
- **WHEN** a workflow resolves active schema context for a knowledge base with an active schema definition
- **THEN** resolution returns the knowledge base identifier, schema definition identifier, schema definition metadata, and parsed schema document
- **AND** exact stored content and content hash are available without exposing a persistence record

#### Scenario: A consumer attempts to modify a snapshot
- **WHEN** a consumer attempts to mutate snapshot metadata or nested schema collections
- **THEN** the snapshot cannot be modified
- **AND** stored schema state and other consumers' snapshots remain unchanged

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

### Requirement: Active schema resolution uses relational associations
The system SHALL resolve a knowledge base's active schema and complete stored content from PostgreSQL relational associations without requiring operational schema or knowledge-base nodes in Neo4j.

#### Scenario: A workflow requests active schema context
- **WHEN** the knowledge base has one active relational association
- **THEN** the resolver returns the same identity and parsed schema content expected by extraction and query workflows

#### Scenario: No active association exists
- **WHEN** a workflow requests an active schema for a knowledge base without one
- **THEN** the existing not-found or precondition behavior is preserved

### Requirement: Query and extraction workflows use shared resolution
Graph extraction, Cypher generation, and Cypher validation workflows SHALL obtain active schema context through the shared resolver rather than duplicating repository lookup and schema parsing logic.

#### Scenario: Workflows require active schema context
- **WHEN** graph extraction, Cypher generation, or Cypher validation needs an active schema
- **THEN** the workflow delegates active schema lookup and parsing to the shared resolver

### Requirement: Expected schema targets are resolved by identity and content hash
The system SHALL support active schema resolution against an expected schema identifier and content hash. Resolution SHALL reject a changed active association or changed content hash using the established immutable-processing-target failure behavior. Schema version SHALL retain its existing identity meaning and SHALL NOT substitute for content hash when inactive content has been replaced.

#### Scenario: Expected target still matches
- **WHEN** the active schema identifier and stored content hash both match the expected target
- **THEN** resolution returns the matching immutable complete schema snapshot

#### Scenario: Active association changed
- **WHEN** the active schema identifier differs from the expected target
- **THEN** resolution rejects the target before extraction proceeds

#### Scenario: Content changed without an identity change
- **WHEN** the expected schema identifier matches but its stored content hash differs
- **THEN** resolution rejects the target before extraction proceeds
