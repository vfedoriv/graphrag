## MODIFIED Requirements

### Requirement: Reprocessing recovery repairs derived state
The system SHALL recover expired claims and SHALL recompute plan total cardinality and outcome counters from authoritative persisted item state while preserving relational count invariants.

#### Scenario: A worker stops after document processing
- **WHEN** the item completion checkpoint is missing
- **THEN** recovery inspects the underlying document-run outcome
- **AND** completes or retries the item idempotently

#### Scenario: Stored counters disagree with items
- **WHEN** recovery detects inconsistent queued, running, succeeded, failed, stale, or blocked counters
- **THEN** all outcome counters are repaired from item states
- **AND** the repaired counters sum to the repaired total document count

#### Scenario: Stored total disagrees with item cardinality
- **WHEN** a plan's stored total document count differs from the number of its persisted items
- **THEN** recovery MUST treat persisted items as authoritative and repair the total document count
- **AND** terminal status MUST be derived only after total and outcome counters agree
- **AND** a metadata-only warning MUST identify the plan and mismatched counts without document content

#### Scenario: One malformed plan cannot be repaired
- **WHEN** a plan-local data inconsistency cannot be converted into invariant-preserving state
- **THEN** recovery MUST report the plan identifier and exception class
- **AND** recovery MUST NOT abort startup or prevent unrelated recoverable plans from being processed
- **AND** infrastructure connectivity and schema migration failures MUST remain startup-fatal
