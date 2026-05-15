## MODIFIED Requirements

### Requirement: Unknown-field tolerance does not weaken schema enforcement
The system MUST continue enforcing schema constraints for labels, relationship types, and allowed properties after deserialization. The system MUST drop relationships with schema-invalid triples and continue processing the remaining valid payload.

#### Scenario: Unknown fields and valid schema output
- **WHEN** a response contains unknown JSON keys but all known labels, relationships, and properties satisfy the active schema
- **THEN** extraction result is accepted for downstream persistence

#### Scenario: Unknown fields and invalid relationship triple
- **WHEN** a response contains unknown JSON keys and includes one or more relationships whose triple (`type|fromLabel|toLabel`) is not allowed by the active schema
- **THEN** those relationships are removed from the extraction result and the chunk continues with remaining valid relationships

#### Scenario: Unknown fields and unknown node label
- **WHEN** a response contains unknown JSON keys and also contains a node with a label outside the active schema
- **THEN** extraction fails with validation error for the unknown node label

### Requirement: Unknown JSON fields are observable
The system SHALL emit warning logs when unknown fields are ignored during extraction response parsing. The system SHALL emit warning logs when schema-invalid relationships are dropped during validation.

#### Scenario: Unknown keys detected
- **WHEN** parsing encounters unknown fields in extraction response payload
- **THEN** the system logs a warning including extraction context and ignored key names

#### Scenario: Invalid relationship dropped
- **WHEN** validation detects a relationship triple that is not in the active schema
- **THEN** the system logs a warning including extraction context and the dropped triple

## ADDED Requirements

### Requirement: Extraction prompt constrains relationship triples explicitly
The extraction prompt SHALL include an explicit list of allowed relationship triples derived from the active schema and SHALL instruct the model to omit relationships that do not match one of those triples.

#### Scenario: Prompt includes allowed triples
- **WHEN** graph extraction prompt is generated for an active schema
- **THEN** the prompt contains the complete set of allowed `type|fromLabel|toLabel` triples

#### Scenario: Prompt includes omit rule
- **WHEN** graph extraction prompt is generated
- **THEN** the prompt states that if no listed triple applies, the relationship must be omitted
