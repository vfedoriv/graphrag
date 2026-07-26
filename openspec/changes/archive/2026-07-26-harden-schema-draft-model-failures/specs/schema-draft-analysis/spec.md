## ADDED Requirements

### Requirement: Draft source failures use structured recovery decisions
The system SHALL classify each failed draft source using a compatible broad failure category, a stable detailed failure code, and an independently determined retryability value based on the bounded exception cause chain and failure stage.

#### Scenario: Nested transport timeout is exhausted
- **WHEN** a source model call fails with a timeout represented by an outer provider exception whose bounded cause chain contains a socket, connection, or read timeout
- **THEN** the source outcome is classified as a retryable timeout
- **AND** the detailed failure code identifies a transport timeout
- **AND** classification does not depend on the outer exception message containing the word `timeout`

#### Scenario: Provider status is available
- **WHEN** the OpenAI-compatible SDK exposes an HTTP status for a failed source call
- **THEN** rate-limit and retryable provider-service statuses receive retryable detailed codes
- **AND** authentication, authorization, invalid-request, and other permanent statuses receive non-retryable detailed codes

#### Scenario: Source state is invalid
- **WHEN** a source snapshot is stale or its content is unavailable before candidate analysis can complete
- **THEN** the source outcome receives a non-retryable source-state failure code
- **AND** a later model output retry is not attempted

### Requirement: Draft candidate analysis retries unusable model output once
The system SHALL make at most one additional application-level output attempt when a completed transport call returns missing, blank, malformed, or candidate-contract-invalid model output, and SHALL NOT add another application retry for transport or provider failures already handled by the SDK.

#### Scenario: First response has blank normal content
- **WHEN** the first model response has no usable normal assistant content
- **THEN** the system makes one additional output attempt for that chunk when the source remains eligible
- **AND** reasoning metadata is not interpreted as candidate output

#### Scenario: Second response is valid
- **WHEN** the first output attempt is unusable and the second output attempt converts and validates successfully
- **THEN** the source succeeds using only candidates from the successful attempt
- **AND** the failed attempt contributes no candidate or alias data

#### Scenario: Both responses are unusable
- **WHEN** both allowed output attempts fail conversion or candidate-contract validation
- **THEN** the source receives a retryable model-output failure outcome
- **AND** no further application-level output attempt is made

#### Scenario: SDK transport retries are exhausted
- **WHEN** a model call terminates with a transport, rate-limit, or provider-service exception after SDK handling
- **THEN** the application output retry does not invoke the model again for that failure
- **AND** the durable source outcome retains its run-level retryability decision

### Requirement: Partial draft run retryability reflects failed outcomes
The system SHALL persist terminal analysis-run retryability from all failed source outcomes regardless of whether the run produced a valid partial aggregate.

#### Scenario: Partial run contains retryable source failure
- **WHEN** at least one source succeeds and at least one failed source outcome is retryable
- **THEN** the run becomes `PARTIAL`
- **AND** the run-level retryable field is true

#### Scenario: Partial run contains only permanent failures
- **WHEN** at least one source succeeds and every failed source outcome is non-retryable
- **THEN** the run becomes `PARTIAL`
- **AND** the run-level retryable field is false

#### Scenario: Partial run is retried
- **WHEN** a client retries a partial run without changing its reuse-key inputs
- **THEN** successful source outcomes are reused without another model call
- **AND** unresolved eligible sources are executed in the new run

