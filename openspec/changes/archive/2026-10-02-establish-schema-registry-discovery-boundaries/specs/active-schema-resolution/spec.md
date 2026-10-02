## MODIFIED Requirements

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

## ADDED Requirements

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
