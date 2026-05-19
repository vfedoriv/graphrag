## MODIFIED Requirements

### Requirement: Transformer extension preserves inferred node and edge properties
The system SHALL use a transformer implementation for schema generation that preserves node and edge `properties` extracted from model output when such properties are present, and SHALL instruct the model to emit property objects as JSON key/value pairs with string values.

#### Scenario: Prompt shows property objects as string key/value pairs
- **WHEN** schema generation builds the extraction prompt
- **THEN** the required output examples for `head_properties`, `relation_properties`, and `tail_properties` use JSON object key/value pairs
- **AND** example property values are shown as strings rather than arrays

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

#### Scenario: Array-valued properties are rejected by schema-generation contract
- **WHEN** model output includes array-valued property entries for schema-generation property maps
- **THEN** schema generation treats this as invalid output shape
- **AND** generation does not silently normalize array values as backward-compatibility behavior

### Requirement: Prompt contract enforces key/property consistency
The system SHALL instruct schema generation models that each node definition `key` may be either a single property name or a list of property names declared in that same node's `properties` list, and SHALL provide practical canonical-key heuristics to improve key selection quality.

#### Scenario: Model receives schema generation prompt
- **WHEN** the system constructs the schema generation prompt
- **THEN** the prompt includes an explicit constraint that every property referenced by node `key` is present in node `properties[].name`
- **AND** the prompt discourages generic `id` unless `id` is explicitly declared as a property

#### Scenario: Prompt includes canonical key guidance examples
- **WHEN** the system constructs schema generation key guidance
- **THEN** the prompt includes examples of good canonical identity properties for common node types
- **AND** the guidance asks the model to propose meaningful key candidates from declared properties instead of defaulting to generic keys

#### Scenario: Prompt allows composite keys
- **WHEN** the system constructs schema generation key guidance
- **THEN** the prompt allows node `key` to contain multiple property names for composite identity
- **AND** the prompt instructs that listed key components must come from declared node properties
