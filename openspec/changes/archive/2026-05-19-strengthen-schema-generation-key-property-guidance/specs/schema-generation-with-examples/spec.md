## ADDED Requirements

### Requirement: Prompt contract enforces key/property consistency
The system SHALL instruct schema generation models that each node definition `key` must match one property name declared in that same node's `properties` list.

#### Scenario: Model receives schema generation prompt
- **WHEN** the system constructs the schema generation prompt
- **THEN** the prompt includes an explicit constraint that node `key` values must be present in node `properties[].name`
- **AND** the prompt discourages generic `id` unless `id` is explicitly declared as a property

### Requirement: Post-generation key/property mismatch advisory
The system SHALL analyze generated schemas and return advisory warnings/suggestions when a node key is not declared in that node properties.

#### Scenario: Generated node key is missing from properties
- **WHEN** a generated schema contains a node where `key` is not present in `properties[].name`
- **THEN** the generation response includes a warning describing the mismatch
- **AND** the generation response includes at least one suggestion to resolve it

#### Scenario: Multiple generated nodes have key/property mismatches
- **WHEN** a generated schema contains key/property mismatches on multiple nodes
- **THEN** the generation response includes warnings for all mismatched nodes
- **AND** warning order follows node order from the generated schema

### Requirement: Advisory checks do not block generation response
The system SHALL return generated schema content even when key/property advisory warnings are present.

#### Scenario: Generated schema has advisory warnings
- **WHEN** advisory key/property checks detect mismatches
- **THEN** schema generation still returns the generated schema payload
- **AND** warnings are attached without converting the response into a hard validation failure
