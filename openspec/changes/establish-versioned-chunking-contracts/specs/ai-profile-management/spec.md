## ADDED Requirements

### Requirement: Typed profile tokenizer selection
The system SHALL allow an AI profile to declare an optional supported `tokenizerId`, include it in profile revision semantics, and return the non-secret configured or resolved tokenizer identity through profile reads.

#### Scenario: Compatible model alias
- **WHEN** an operator saves a supported explicit tokenizer for an embedding-model alias
- **THEN** subsequent processing resolves that tokenizer from the saved profile revision

#### Scenario: Invalid tokenizer update
- **WHEN** an operator submits an unsupported tokenizer identifier
- **THEN** the API returns a validation problem and leaves the prior profile revision unchanged
