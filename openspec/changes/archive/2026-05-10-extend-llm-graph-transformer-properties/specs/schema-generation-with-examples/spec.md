## ADDED Requirements

### Requirement: Transformer extension preserves inferred node and edge properties
The system SHALL use a transformer implementation for schema generation that preserves node and edge `properties` extracted from model output when such properties are present.

#### Scenario: Node properties are preserved from transformer output
- **WHEN** schema generation processes model output containing node property maps
- **THEN** the graph document used by schema inference contains non-empty `GraphNode.properties` entries for corresponding nodes

#### Scenario: Edge properties are preserved from transformer output
- **WHEN** schema generation processes model output containing edge property maps
- **THEN** the graph document used by schema inference contains non-empty `GraphEdge.properties` entries for corresponding relationships

#### Scenario: Missing properties do not break transformation
- **WHEN** model output omits node or edge properties
- **THEN** transformation succeeds without error
- **AND** the resulting node or edge properties remain empty rather than causing invalid graph output

### Requirement: Transformer extension behavior is regression tested
The system SHALL include automated tests for transformer extension behavior to ensure node and edge property extraction remains stable.

#### Scenario: Unit tests validate node and edge property extraction
- **WHEN** transformer extension tests run with representative model output fixtures
- **THEN** tests verify that node and edge properties are parsed and populated as expected
- **AND** tests fail if properties are dropped unexpectedly
