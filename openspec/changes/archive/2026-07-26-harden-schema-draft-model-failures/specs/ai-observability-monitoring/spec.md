## ADDED Requirements

### Requirement: Schema discovery observations include safe attempt diagnostics
The system SHALL attach privacy-safe attempt, response, and failure metadata to each schema-discovery model observation without using high-cardinality values as metric tags.

#### Scenario: Candidate model output succeeds
- **WHEN** a candidate model call returns usable normal assistant content
- **THEN** its observation includes the logical output attempt, elapsed time, response model and identifier when available, finish reason, token usage, and response length/fingerprint metadata
- **AND** low-cardinality metrics retain only bounded status, workflow, provider, model, and failure-category dimensions

#### Scenario: Candidate model output is unusable
- **WHEN** a model response is missing, blank, malformed, or candidate-contract-invalid
- **THEN** the failed attempt observation includes the stable detailed failure code and all available safe response metadata
- **AND** it records reasoning-content presence or length only, never reasoning content

#### Scenario: Model call fails before a response
- **WHEN** a transport or provider failure prevents a model response
- **THEN** the observation includes configured timeout/retry metadata, elapsed time, bounded exception-chain types, root exception type, provider status when available, and a message fingerprint
- **AND** response-only fields are absent rather than fabricated

