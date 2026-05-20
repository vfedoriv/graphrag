# graph-identity-persistence Specification

## Purpose
TBD - created by archiving change harden-schema-generation-graph-identity. Update Purpose after archive.
## Requirements
### Requirement: Extracted node identity is collision resistant
The system SHALL derive persisted extracted node identifiers from canonical identity material that preserves boundaries between schema id, node label, ordered key names, and ordered key values.

#### Scenario: Composite key value contains delimiter characters
- **WHEN** an extracted node key value contains delimiter characters such as `|`
- **THEN** the persisted node identifier remains distinct from identifiers for different ordered key names or values
- **AND** the node is not merged with a different logical entity because of delimiter ambiguity

#### Scenario: Same identity material is written repeatedly
- **WHEN** the same schema id, node label, ordered key names, and ordered key values are written more than once
- **THEN** the system derives the same persisted node identifier each time
- **AND** Neo4j `MERGE` remains idempotent for that logical entity

### Requirement: Extracted relationship identity is collision resistant
The system SHALL derive persisted extracted relationship identifiers from canonical relationship identity material that preserves boundaries between schema id, relationship type, source node identity, and target node identity.

#### Scenario: Endpoint identifiers contain delimiter characters
- **WHEN** a relationship endpoint identifier or relationship type contains delimiter-like characters after safe schema validation
- **THEN** the persisted relationship identifier remains distinct from identifiers for different relationship identity material

#### Scenario: Same relationship identity is written repeatedly
- **WHEN** the same schema id, relationship type, source node identity, and target node identity are written more than once
- **THEN** the system derives the same persisted relationship identifier each time
- **AND** Neo4j `MERGE` remains idempotent for that logical relationship

### Requirement: Graph writes reject incomplete identity material
The system MUST NOT persist extracted graph nodes or relationships when required identity material is incomplete after extraction normalization and filtering.

#### Scenario: Node key component is blank before write
- **WHEN** graph persistence receives an extracted node that is missing any required key component for its schema node definition
- **THEN** graph persistence is rejected before deriving the node identifier

#### Scenario: Relationship endpoint key component is blank before write
- **WHEN** graph persistence receives an extracted relationship that is missing any required key component for the endpoint schema node definition
- **THEN** graph persistence is rejected before deriving endpoint or relationship identifiers

#### Scenario: Validation filters incomplete identity before write
- **WHEN** extraction validation receives model output with incomplete node or relationship identity material that cannot be repaired
- **THEN** validation omits the invalid element from the graph extraction result passed to persistence
- **AND** graph persistence receives only elements with complete identity material

