## ADDED Requirements

### Requirement: Extraction parser tolerates unknown JSON fields
The system SHALL accept graph extraction model responses that contain unknown JSON fields and SHALL ignore those fields during deserialization.

#### Scenario: Unknown node field is present
- **WHEN** the model response includes an extra node field not defined in extraction DTOs (for example `id`)
- **THEN** deserialization succeeds and extraction continues using known fields

#### Scenario: Unknown relationship field is present
- **WHEN** the model response includes an extra relationship field not defined in extraction DTOs
- **THEN** deserialization succeeds and extraction continues using known fields

### Requirement: Unknown-field tolerance does not weaken schema enforcement
The system MUST continue enforcing schema constraints for labels, relationship types, and allowed properties after deserialization.

#### Scenario: Unknown fields and valid schema output
- **WHEN** a response contains unknown JSON keys but all known labels, relationships, and properties satisfy the active schema
- **THEN** extraction result is accepted for downstream persistence

#### Scenario: Unknown fields and schema violation
- **WHEN** a response contains unknown JSON keys and also contains a known-field schema violation
- **THEN** extraction fails with validation error for the schema violation

### Requirement: Unknown JSON fields are observable
The system SHALL emit warning logs when unknown fields are ignored during extraction response parsing.

#### Scenario: Unknown keys detected
- **WHEN** parsing encounters unknown fields in extraction response payload
- **THEN** the system logs a warning including extraction context and ignored key names
