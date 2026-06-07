## MODIFIED Requirements

### Requirement: Node keys are declared properties
The system SHALL reject schema definitions where any node definition `key` component does not match one of that node definition's declared property names.

#### Scenario: Single node key is declared
- **WHEN** a schema definition contains a node with `key` equal to one of the node's property names
- **THEN** schema validation accepts that node key rule

#### Scenario: Composite node key components are declared
- **WHEN** a schema definition contains a node with multiple `key` components and every component is present in that node's property list
- **THEN** schema validation accepts that node key rule

#### Scenario: Node key is missing from properties
- **WHEN** a schema definition contains a node with any `key` component that is not present in that node's property list
- **THEN** schema validation rejects the schema
- **AND** the validation error identifies the node key component as missing from declared properties

#### Scenario: Node has no properties for key
- **WHEN** a schema definition contains a node with a non-blank `key` and a missing or empty property list
- **THEN** schema validation rejects the schema
- **AND** the validation error identifies that the key must be declared as a property

#### Scenario: Schema update rejects node key missing from properties
- **WHEN** a client updates a schema with content containing a node `key` component that is not present in that node's property list
- **THEN** the system rejects the update as invalid
- **AND** the persisted schema content and content hash are unchanged
