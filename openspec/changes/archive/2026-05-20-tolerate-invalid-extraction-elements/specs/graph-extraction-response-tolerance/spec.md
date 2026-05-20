## MODIFIED Requirements

### Requirement: Unknown-field tolerance does not weaken schema enforcement
The system MUST continue enforcing schema constraints for labels, relationship types, and allowed properties after deserialization. The system MUST drop schema-invalid extracted nodes and relationships and continue processing the remaining valid payload.

#### Scenario: Unknown fields and valid schema output
- **WHEN** a response contains unknown JSON keys but all known labels, relationships, and properties satisfy the active schema
- **THEN** extraction result is accepted for downstream persistence

#### Scenario: Unknown fields and invalid relationship triple
- **WHEN** a response contains unknown JSON keys and includes one or more relationships whose triple (`type|fromLabel|toLabel`) is not allowed by the active schema
- **THEN** those relationships are removed from the extraction result and the chunk continues with remaining valid relationships

#### Scenario: Unknown fields and unknown node label
- **WHEN** a response contains unknown JSON keys and also contains a node with a label outside the active schema
- **THEN** that node is removed from the extraction result
- **AND** the chunk continues with remaining valid nodes and relationships

### Requirement: Unknown JSON fields are observable
The system SHALL emit warning logs when unknown fields are ignored during extraction response parsing. The system SHALL emit warning logs when schema-invalid extracted nodes or relationships are dropped during validation.

#### Scenario: Unknown keys detected
- **WHEN** parsing encounters unknown fields in extraction response payload
- **THEN** the system logs a warning including extraction context and ignored key names

#### Scenario: Invalid relationship dropped
- **WHEN** validation detects a relationship triple that is not in the active schema
- **THEN** the system logs a warning including extraction context and the dropped triple

#### Scenario: Invalid node dropped
- **WHEN** validation detects a node label or node identity that is not valid for the active schema
- **THEN** the system logs a warning including extraction context and the dropped node label or sanitized identity context
