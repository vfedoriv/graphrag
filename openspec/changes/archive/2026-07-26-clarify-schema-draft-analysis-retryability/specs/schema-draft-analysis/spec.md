## ADDED Requirements

### Requirement: Analysis retryability concepts are explicit and consistent
The system SHALL expose persisted failure retryability separately from current retry-command eligibility in both analysis-run detail and history responses. The `retryable` field SHALL report the persisted run-level failure classification, while `canRetry` SHALL report whether deterministic current resource-state preconditions permit requesting a child retry run.

#### Scenario: Completed run remains eligible for explicit reanalysis
- **WHEN** a completed analysis has no retryable failures and the open draft otherwise permits another analysis
- **THEN** detail and history report `retryable` as false
- **AND** detail and history report `canRetry` as true

#### Scenario: Retryable failure cannot be retried while another run is active
- **WHEN** a terminal analysis has a persisted retryable failure but the draft already has a running analysis
- **THEN** detail and history report `retryable` as true
- **AND** detail and history report `canRetry` as false

#### Scenario: Permanent failure remains distinguishable from command eligibility
- **WHEN** a partial or failed analysis contains only non-retryable source failures but current draft state permits a new analysis
- **THEN** detail and history report `retryable` as false
- **AND** detail and history report `canRetry` as true

#### Scenario: Retry eligibility changes after a history response
- **WHEN** resource state changes or a competing analysis starts after `canRetry` was read
- **THEN** the retry command revalidates current state authoritatively
- **AND** the earlier `canRetry` value does not guarantee command acceptance

### Requirement: Analysis retry validates shared resource-state eligibility
The system SHALL validate retry requests using the same deterministic resource-state eligibility rules used to derive `canRetry`, and SHALL NOT use persisted failure retryability as a command gate.

#### Scenario: Client retries a completed run
- **WHEN** a client retries a completed run for an open draft with active eligible sources and no running analysis
- **THEN** the system accepts a new child run
- **AND** matching successful source outcomes remain eligible for reuse
- **AND** the child records the completed run identifier as `retryOfRunId`

#### Scenario: Client retries a run with only permanent failures
- **WHEN** a client retries a terminal run whose persisted `retryable` value is false and current resource-state eligibility is satisfied
- **THEN** the system accepts the retry without treating persisted failure classification as a rejection reason

#### Scenario: Draft has no active eligible source
- **WHEN** a client requests retry after the draft has no active eligible source
- **THEN** detail and history report `canRetry` as false
- **AND** the retry command rejects the request without creating a run

#### Scenario: Draft is not mutable
- **WHEN** the owning draft is no longer open or the supplied optimistic revision is stale
- **THEN** the retry command rejects the request without creating a run
- **AND** a non-open draft reports `canRetry` as false

#### Scenario: Another analysis is running
- **WHEN** the draft has a running analysis
- **THEN** every historical run reports `canRetry` as false
- **AND** a retry request that races with or follows that running analysis is rejected or returns the existing matching run according to normal analysis-start idempotency

## MODIFIED Requirements

### Requirement: Analysis run history is discoverable and lineage-aware
The system SHALL provide a paginated analysis-run history for an owned draft whose summaries include identifiers, statuses, source counts, timestamps, persisted failure retryability, current retry-command eligibility, retry parent, aggregate identifier, and derived currentness.

#### Scenario: Client recovers analysis after losing the run identifier
- **WHEN** a client lists analysis runs for an owned draft
- **THEN** the system returns a bounded page ordered by creation time descending with deterministic ties
- **AND** each summary contains enough state to resume polling, decide whether a retry action is currently available, or open the detailed status resource

#### Scenario: Running analysis still matches the draft
- **WHEN** a queued or running analysis snapshot matches the current draft revision, guidance, and active source membership
- **THEN** its summary is marked current

#### Scenario: Completed analysis produced the current aggregate
- **WHEN** a terminal analysis run's aggregate identifier equals the draft's current aggregate identifier
- **THEN** its summary is marked current even if an older persisted flag is inconsistent

#### Scenario: Draft changed after analysis
- **WHEN** an analysis snapshot and aggregate no longer match current draft state
- **THEN** its history remains visible
- **AND** its summary is marked non-current

#### Scenario: History exposes distinct retryability values
- **WHEN** a client lists a terminal analysis run
- **THEN** `retryable` equals the run's persisted failure retryability
- **AND** `canRetry` is derived from current retry-command resource-state eligibility

#### Scenario: Analysis is retried
- **WHEN** a client retries a terminal analysis run and a new run is created
- **THEN** the new run records the selected run identifier as `retryOfRunId`
- **AND** historical runs created before lineage support remain valid roots with a null parent
