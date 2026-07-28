## ADDED Requirements

### Requirement: Draft analysis uses an atomic relational claim
The system SHALL start analysis by conditionally assigning the running analysis run to the draft only when no active claim exists and expected revision currentness holds.

#### Scenario: Two workers claim one draft
- **WHEN** concurrent workers attempt to start analysis for the same draft revision
- **THEN** only one conditional relational update succeeds
- **AND** no standalone lease record is required

#### Scenario: A stale worker completes
- **WHEN** a worker no longer owns the draft's current analysis claim
- **THEN** its output cannot replace the current aggregate or terminal run state

### Requirement: Analysis history and recovery are durable
The system SHALL persist analysis runs, per-source results, aggregate revisions, retry lineage, reuse keys, and recovery state in PostgreSQL while preserving deterministic payload fingerprints.

#### Scenario: Analysis completes
- **WHEN** all eligible source results are aggregated
- **THEN** the aggregate revision and terminal run state commit relationally
- **AND** stored text payloads preserve existing fingerprint inputs

#### Scenario: Analysis becomes stale
- **WHEN** recovery finds an expired or abandoned claim
- **THEN** the run and draft claim are repaired conditionally
- **AND** retry eligibility follows existing policy
