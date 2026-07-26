## ADDED Requirements

### Requirement: Live discovery budgets govern subsequent draft runs
The system SHALL apply live schema-discovery source-concurrency, source-timeout, and request-timeout settings to subsequently created durable schema-draft analysis runs through an immutable typed execution-policy snapshot.

#### Scenario: Source concurrency changes
- **WHEN** a client updates `app.schema-discovery.max-concurrency` with a valid live value
- **THEN** a subsequently created draft run captures and enforces the updated per-run source-concurrency limit
- **AND** already created runs retain their prior captured limit

#### Scenario: Discovery timeouts change
- **WHEN** a client updates the live schema-discovery source or request timeout with a valid value
- **THEN** a subsequently created draft run captures and enforces the updated timeout
- **AND** its reuse settings fingerprint and effective budget metadata describe the same captured policy

#### Scenario: Live override is cleared
- **WHEN** a client clears a persisted discovery execution-budget override
- **THEN** subsequently created draft runs capture the startup default
- **AND** existing runs retain the policy captured when they were created

### Requirement: Discovery budgets remain distinct from AI profile transport settings
The system SHALL represent discovery source/request deadlines separately from AI-profile HTTP timeout and SDK retry settings and SHALL NOT silently rewrite either configuration to match the other.

#### Scenario: Provider retry envelope exceeds workflow deadline
- **WHEN** an AI profile's configured timeout and retry envelope can outlast a captured source or request deadline
- **THEN** the draft run still enforces its captured workflow deadline as a result-acceptance boundary
- **AND** the backend emits privacy-safe configuration metadata sufficient to diagnose the mismatch
- **AND** the stored AI profile remains unchanged
