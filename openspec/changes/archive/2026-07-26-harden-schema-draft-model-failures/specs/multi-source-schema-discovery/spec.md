## ADDED Requirements

### Requirement: Candidate output recovery is bounded and provider-aware
The system SHALL distinguish unusable completed model output from SDK transport/provider failures and SHALL apply bounded output recovery consistently to candidate extraction.

#### Scenario: Candidate JSON cannot be converted
- **WHEN** normal assistant content is present but cannot be converted into the typed candidate container
- **THEN** the system may make one additional output attempt
- **AND** a second conversion failure produces a retryable malformed-model-response outcome

#### Scenario: Candidate coordinates are invalid
- **WHEN** converted model output omits required candidate coordinates, supplies invalid confidence, or violates the node-key contract
- **THEN** the system may make one additional output attempt
- **AND** invalid candidates from every failed attempt are excluded from aggregation

#### Scenario: Permanent provider request failure occurs
- **WHEN** the provider rejects authentication, authorization, request options, or another permanent request condition
- **THEN** candidate extraction does not make an application-level output retry
- **AND** the source receives a non-retryable provider failure decision

### Requirement: Discovery failure classification is reusable across workflows
The system SHALL use the same structured failure-decision semantics for synchronous multi-source discovery and durable draft analysis while allowing each API to preserve its established response envelope.

#### Scenario: Equivalent failures occur in both workflows
- **WHEN** synchronous discovery and draft analysis encounter equivalent timeout, rate-limit, empty-output, conversion, or candidate-validation failures
- **THEN** both workflows assign compatible broad categories, detailed codes, and retryability decisions
- **AND** neither workflow relies solely on an outer exception message

