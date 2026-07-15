## MODIFIED Requirements

### Requirement: Analysis persists incremental progress and supports retry
The system SHALL persist each source outcome independently so interruption or partial failure does not discard completed source work, and SHALL retain privacy-safe progress and failure diagnostics for a failed source.

#### Scenario: All eligible sources succeed
- **WHEN** every source in the analysis snapshot completes or is validly reused
- **THEN** the run becomes `COMPLETED`
- **AND** the system persists a deterministic aggregate revision and per-source results

#### Scenario: Some sources fail
- **WHEN** at least one source succeeds and at least one source fails
- **THEN** the run becomes `PARTIAL`
- **AND** successful results and the partial aggregate remain available

#### Scenario: All sources fail
- **WHEN** no source completes successfully or is reusable
- **THEN** the run becomes `FAILED`
- **AND** it does not replace the draft's previously current aggregate

#### Scenario: Failed sources are retried
- **WHEN** a client retries a partial or failed run
- **THEN** a new run reuses matching successful source results and executes only failed, interrupted, stale, or newly eligible sources

#### Scenario: Application restarts during a run
- **WHEN** the application starts with a run still marked `RUNNING`
- **THEN** the interrupted run is closed with a retryable interruption outcome
- **AND** already completed source results remain eligible for reuse by retry

#### Scenario: Prepared source fails during candidate analysis
- **WHEN** source preparation produced analysis chunks and candidate model invocation or conversion later fails
- **THEN** the failed source outcome records the number of prepared chunks rather than zero
- **AND** the operational warning includes the exception type and a non-reversible exception-message fingerprint
- **AND** the warning does not include source content, prompts, normal or reasoning model output, or candidate payloads

#### Scenario: Source fails before preparation completes
- **WHEN** a source fails before any prepared analysis chunks are available
- **THEN** the failed source outcome records a chunk count of zero
- **AND** it retains the privacy-safe failure category and retryability metadata
