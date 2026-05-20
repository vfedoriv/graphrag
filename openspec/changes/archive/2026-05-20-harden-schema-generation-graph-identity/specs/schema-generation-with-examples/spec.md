## MODIFIED Requirements

### Requirement: Prompt contract enforces key/property consistency
The system SHALL instruct schema generation models that each node definition `key` may be either a single property name or a list of property names declared in that same node's `properties` list, and SHALL provide practical canonical-key heuristics to improve key selection quality.

#### Scenario: Model receives schema generation prompt
- **WHEN** the system constructs the schema generation prompt
- **THEN** the prompt includes a grammatically complete explicit constraint that every property referenced by node `key` is present in that same node's `properties[].name`
- **AND** the prompt discourages generic `id` unless `id` is explicitly declared as a property

#### Scenario: Prompt includes canonical key guidance examples
- **WHEN** the system constructs schema generation key guidance
- **THEN** the prompt includes examples of good canonical identity properties for common node types
- **AND** the guidance asks the model to propose meaningful key candidates from declared properties instead of defaulting to generic keys

#### Scenario: Prompt allows composite keys
- **WHEN** the system constructs schema generation key guidance
- **THEN** the prompt allows node `key` to contain multiple property names for composite identity
- **AND** the prompt instructs that listed key components must come from declared node properties

### Requirement: Post-generation key/property mismatch advisory
The system SHALL analyze generated schemas and return advisory warnings/suggestions when a node key is not declared in that node properties.

#### Scenario: Generated node key is missing from properties
- **WHEN** a generated schema contains a node where any `key` component is not present in `properties[].name`
- **THEN** the generation response includes a warning describing each missing key component
- **AND** the generation response includes at least one suggestion to resolve it

#### Scenario: Multiple generated nodes have key/property mismatches
- **WHEN** a generated schema contains key/property mismatches on multiple nodes
- **THEN** the generation response includes warnings for all mismatched nodes
- **AND** warning order follows node order from the generated schema

#### Scenario: Generated key candidate is not a safe declared property
- **WHEN** schema generation receives a model-provided key candidate that is blank, duplicated, unsafe, or absent from the generated node properties
- **THEN** the generated schema does not silently treat that candidate as valid identity material
- **AND** the generation response includes an advisory warning when caller action is required

### Requirement: Advisory checks do not block generation response
The system SHALL return generated schema content even when key/property advisory warnings are present.

#### Scenario: Generated schema has advisory warnings
- **WHEN** advisory key/property checks detect mismatches
- **THEN** schema generation still returns the generated schema payload
- **AND** warnings are attached without converting the response into a hard validation failure

#### Scenario: No key can be inferred from generated properties
- **WHEN** generated node properties do not contain any usable key candidate
- **THEN** schema generation still returns the generated schema payload
- **AND** the response includes an advisory warning rather than fabricating an undeclared `id` key
