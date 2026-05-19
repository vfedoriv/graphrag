## ADDED Requirements

### Requirement: Node keys are declared properties
The system SHALL reject schema definitions where a node definition's `key` does not match one of that node definition's declared property names.

#### Scenario: Node key is declared
- **WHEN** a schema definition contains a node with `key` equal to one of the node's property names
- **THEN** schema validation accepts that node key rule

#### Scenario: Node key is missing from properties
- **WHEN** a schema definition contains a node with `key` that is not present in that node's property list
- **THEN** schema validation rejects the schema
- **AND** the validation error identifies the node key as missing from declared properties

#### Scenario: Node has no properties for key
- **WHEN** a schema definition contains a node with a non-blank `key` and a missing or empty property list
- **THEN** schema validation rejects the schema
- **AND** the validation error identifies that the key must be declared as a property
