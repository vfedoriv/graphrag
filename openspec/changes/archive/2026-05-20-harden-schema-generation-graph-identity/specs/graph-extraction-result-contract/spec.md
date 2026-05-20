## ADDED Requirements

### Requirement: Relationship endpoint keys are complete after normalization
The system SHALL require every extracted relationship endpoint key to contain all non-blank key components required by the endpoint node's schema definition after extraction normalization and any supported endpoint-key fill behavior.

#### Scenario: Composite endpoint key is complete
- **WHEN** an extracted relationship endpoint references a node whose schema key contains multiple components
- **AND** the endpoint key contains non-blank values for every required component
- **THEN** extraction validation accepts the endpoint key for downstream persistence

#### Scenario: Composite endpoint key is partially missing
- **WHEN** an extracted relationship endpoint references a node whose schema key contains multiple components
- **AND** the endpoint key is missing at least one required component after normalization
- **THEN** extraction validation rejects the payload before graph persistence
- **AND** the error identifies the relationship endpoint key as incomplete

### Requirement: Persisted extracted properties are schema constrained
The system SHALL persist only schema-declared domain properties from extracted node and relationship property maps, plus system metadata properties required for provenance and operation.

#### Scenario: Extracted node contains undeclared property
- **WHEN** an extracted node property map contains a property that is not declared for that node label in the active schema
- **THEN** graph persistence omits that undeclared property from the Neo4j node
- **AND** declared properties and system metadata remain eligible for persistence

#### Scenario: Extracted relationship contains undeclared property
- **WHEN** an extracted relationship property map contains a property that is not declared for that relationship type and endpoints in the active schema
- **THEN** graph persistence omits that undeclared property from the Neo4j relationship
- **AND** declared properties and system metadata remain eligible for persistence
