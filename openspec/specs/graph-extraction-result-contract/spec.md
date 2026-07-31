# graph-extraction-result-contract Specification

## Purpose
TBD - created by archiving change harden-extraction-cleanup-and-schema-validation. Update Purpose after archive.
## Requirements
### Requirement: Extraction result collections are never null
The system SHALL normalize graph extraction result node and relationship collections to empty collections when an extraction client supplies null collections.

#### Scenario: Extraction client returns null node list
- **WHEN** a graph extraction result is created with a null node collection
- **THEN** the result exposes an empty node collection to validation and graph persistence

#### Scenario: Extraction client returns null relationship list
- **WHEN** a graph extraction result is created with a null relationship collection
- **THEN** the result exposes an empty relationship collection to validation and graph persistence

#### Scenario: Extraction client returns populated collections
- **WHEN** a graph extraction result is created with populated node or relationship collections
- **THEN** the result exposes those collections without dropping entries

### Requirement: Relationship endpoint keys are complete after normalization
The system SHALL keep only extracted relationships whose endpoint keys contain all non-blank key components required by the endpoint node's schema definition after extraction normalization and any supported endpoint-key fill behavior.

#### Scenario: Composite endpoint key is complete
- **WHEN** an extracted relationship endpoint references a node whose schema key contains multiple components
- **AND** the endpoint key contains non-blank values for every required component
- **THEN** extraction validation accepts the endpoint key for downstream persistence

#### Scenario: Composite endpoint key is partially missing
- **WHEN** an extracted relationship endpoint references a node whose schema key contains multiple components
- **AND** the endpoint key is missing at least one required component after normalization
- **THEN** extraction validation omits that relationship from the returned extraction result
- **AND** validation continues processing other valid relationships from the same response

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

### Requirement: Extraction validation returns persistable graph elements only
The system SHALL return a graph extraction result containing only nodes and relationships that passed schema validation, supported normalization, and supported filtering.

#### Scenario: Mixed valid and invalid extracted nodes
- **WHEN** an extraction response contains both valid nodes and nodes that cannot be made schema-valid
- **THEN** validation returns the valid nodes
- **AND** validation omits the invalid nodes from the returned extraction result

#### Scenario: Mixed valid and invalid extracted relationships
- **WHEN** an extraction response contains both valid relationships and relationships that cannot be made schema-valid
- **THEN** validation returns the valid relationships
- **AND** validation omits the invalid relationships from the returned extraction result

### Requirement: Parent-scoped graph extraction
Graph extraction SHALL use a persisted bounded parent as its extraction unit and SHALL store that parent ID and its revisioned source scope as the authoritative source chunk for every derived evidence record.

#### Scenario: Fact extracted from parent
- **WHEN** graph extraction accepts a node or relationship from a parent context
- **THEN** its evidence references that parent and not an inferred child
