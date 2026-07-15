## ADDED Requirements

### Requirement: Analysis run history is discoverable and lineage-aware
The system SHALL provide a paginated analysis-run history for an owned draft whose summaries include identifiers, statuses, source counts, timestamps, retryability, retry parent, aggregate identifier, and derived currentness.

#### Scenario: Client recovers analysis after losing the run identifier
- **WHEN** a client lists analysis runs for an owned draft
- **THEN** the system returns a bounded page ordered by creation time descending with deterministic ties
- **AND** each summary contains enough state to resume polling or open the detailed status resource

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

#### Scenario: Analysis is retried
- **WHEN** a client retries a terminal analysis run and a new run is created
- **THEN** the new run records the selected run identifier as `retryOfRunId`
- **AND** historical runs created before lineage support remain valid roots with a null parent
