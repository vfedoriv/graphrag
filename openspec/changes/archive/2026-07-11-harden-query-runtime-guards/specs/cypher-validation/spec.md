## ADDED Requirements

### Requirement: Explicit limits are validated against effective runtime policy
The system SHALL validate executable top-level Cypher limits, including bound limit parameters, against the effective runtime maximum rows setting.

#### Scenario: Explicit limit is within policy
- **WHEN** a validated query has an explicit top-level limit less than or equal to the effective runtime maximum
- **THEN** validation preserves that limit
- **AND** the validation response reports the effective maximum and timeout policy

#### Scenario: Limit text occurs in a literal or comment
- **WHEN** text resembling a `LIMIT` clause occurs only inside a Cypher literal or comment
- **THEN** validation does not treat that text as an executable limit

### Requirement: Validation reports the applied runtime query policy
The system SHALL return the runtime query policy snapshot used for validation in validation, generation, ask, and execution responses.

#### Scenario: Live settings override startup defaults
- **WHEN** a client updates a live query maximum or timeout setting before a query request
- **THEN** every query response for that request reports the overridden effective value
- **AND** validation and execution use the same effective value
