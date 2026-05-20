## MODIFIED Requirements

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

## ADDED Requirements

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
