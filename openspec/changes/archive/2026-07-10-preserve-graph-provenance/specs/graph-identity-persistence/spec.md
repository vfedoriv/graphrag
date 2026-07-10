## ADDED Requirements

### Requirement: Canonical fact identity excludes source provenance
The system SHALL derive canonical extracted relationship identity from schema, relationship type, and canonical endpoints without document, chunk, or extraction-run provenance.

#### Scenario: Sources assert the same logical relationship
- **WHEN** different documents assert the same schema-scoped relationship between the same canonical endpoints
- **THEN** the system derives one canonical relationship identity
- **AND** it derives distinct source evidence identities

## MODIFIED Requirements

### Requirement: Extracted relationship identity is collision resistant
The system SHALL derive persisted canonical extracted relationship identifiers from canonical relationship identity material that preserves boundaries between schema id, relationship type, source node identity, and target node identity. The system SHALL derive extraction-evidence identifiers separately from document, chunk, extraction-run, and canonical fact identity material.

#### Scenario: Endpoint identifiers contain delimiter characters
- **WHEN** a relationship endpoint identifier or relationship type contains delimiter-like characters after safe schema validation
- **THEN** the persisted canonical relationship identifier remains distinct from identifiers for different relationship identity material

#### Scenario: Same relationship identity is written repeatedly
- **WHEN** the same schema id, relationship type, source node identity, and target node identity are written more than once
- **THEN** the system derives the same persisted canonical relationship identifier each time
- **AND** Neo4j `MERGE` remains idempotent for that logical relationship

#### Scenario: Repeated relationship identity has different sources
- **WHEN** the same canonical relationship is written by different document chunks or extraction runs
- **THEN** the system preserves the one canonical relationship identifier
- **AND** the system derives distinct evidence identities without overwriting retained source provenance
