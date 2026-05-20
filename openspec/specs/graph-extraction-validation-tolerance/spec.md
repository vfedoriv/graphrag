# graph-extraction-validation-tolerance Specification

## Purpose
TBD - created by archiving change tolerate-invalid-extraction-elements. Update Purpose after archive.
## Requirements
### Requirement: Extraction validation filters invalid nodes
The system SHALL validate model-produced extracted nodes by returning only nodes that can be normalized into schema-valid, persistable graph nodes.

#### Scenario: Unknown node label is skipped
- **WHEN** an extraction response contains a node whose label is not declared by the active schema
- **THEN** validation omits that node from the returned extraction result
- **AND** validation continues processing other nodes and relationships from the same response

#### Scenario: Missing node key is repaired deterministically
- **WHEN** an extracted node is missing a required key component
- **AND** validation can fill the component from supported non-blank node properties using a deterministic rule
- **THEN** validation returns the node with the repaired key component
- **AND** logs that the node key component was repaired

#### Scenario: Missing node key cannot be repaired
- **WHEN** an extracted node is missing a required key component
- **AND** validation cannot repair the component deterministically
- **THEN** validation omits that node from the returned extraction result
- **AND** logs that the node was dropped because required identity material was incomplete

### Requirement: Extraction validation filters invalid relationships
The system SHALL validate model-produced extracted relationships by returning only relationships whose type, labels, endpoint keys, and endpoint references remain schema-valid after normalization and supported repair.

#### Scenario: Relationship endpoint key is repaired from one matching node
- **WHEN** an extracted relationship is missing an endpoint key component
- **AND** exactly one kept extracted node has the endpoint label and a non-blank value for the missing component
- **THEN** validation returns the relationship with the repaired endpoint key component
- **AND** logs that the endpoint key component was repaired

#### Scenario: Relationship endpoint key cannot be repaired
- **WHEN** an extracted relationship is missing an endpoint key component
- **AND** validation cannot repair the component deterministically
- **THEN** validation omits that relationship from the returned extraction result
- **AND** validation continues processing other relationships from the same response

#### Scenario: Relationship references dropped node identity
- **WHEN** an extracted relationship endpoint references a node identity that is not present in the kept node set for that label
- **THEN** validation omits that relationship from the returned extraction result
- **AND** logs that the relationship was dropped because its endpoint did not resolve to kept node data

### Requirement: Extraction validation findings are observable
The system SHALL log validation findings for dropped and repaired extraction elements using sanitized context.

#### Scenario: Invalid extraction element is dropped
- **WHEN** validation drops an extracted node or relationship
- **THEN** the system logs the schema name, element kind, reason, and sanitized element context

#### Scenario: Extraction element is repaired
- **WHEN** validation repairs an extracted node key or relationship endpoint key
- **THEN** the system logs the schema name, element kind, repair reason, and sanitized element context

### Requirement: Extraction validation preserves fatal guardrails
The system MUST fail validation for invalid process inputs and configured extraction guardrail violations instead of silently returning a partial result.

#### Scenario: Null extraction result
- **WHEN** validation receives a null extraction result
- **THEN** validation fails with a graph extraction validation error

#### Scenario: Payload exceeds configured limits
- **WHEN** an extraction response contains more nodes or relationships than the configured per-chunk maximum
- **THEN** validation fails with a graph extraction validation error
- **AND** no graph data from that response is persisted

