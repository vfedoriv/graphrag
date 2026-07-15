## ADDED Requirements

### Requirement: Evaluation run history is discoverable with explicit currentness
The system SHALL provide a paginated evaluation-run history for an owned draft whose summaries include identifiers, statuses, document counts, timestamps, retryability, retry lineage, reproducibility revisions, and derived currentness.

#### Scenario: Client recovers evaluation after losing the run identifier
- **WHEN** a client lists evaluation runs for an owned draft
- **THEN** the system returns a bounded page ordered by creation time descending with deterministic ties
- **AND** each summary identifies the detailed status resource and retry parent when present

#### Scenario: Evaluation matches the current reviewed projection
- **WHEN** an evaluation's draft revision, aggregate revision, and projection content hash match current draft state
- **THEN** its summary is marked current

#### Scenario: Review changes after evaluation
- **WHEN** guidance or a decision changes the effective reviewed projection after an evaluation starts or completes
- **THEN** the evaluation remains in history
- **AND** its summary is marked non-current

### Requirement: Held-out document eligibility is discoverable and authoritative
The system SHALL provide a paginated evaluation-eligible-document view for an owned draft and SHALL use the same eligibility decision when an evaluation is started.

#### Scenario: Document contributed to the current aggregate
- **WHEN** an owned document-backed source revision completed successfully in the analysis run that produced the current aggregate
- **THEN** the eligibility response marks that document ineligible with reason `ACTIVE_DISCOVERY_EVIDENCE`
- **AND** evaluation start rejects selection of that document before model calls begin

#### Scenario: Active document source did not contribute to the current aggregate
- **WHEN** an owned document is active as a draft source but its revision did not successfully contribute to the current aggregate
- **THEN** the eligibility response does not exclude it as active discovery evidence

#### Scenario: Document is eligible
- **WHEN** an owned knowledge-base document did not contribute active evidence to the current aggregate
- **THEN** the response marks it eligible with no ineligibility reason

#### Scenario: Draft changes between eligibility and start
- **WHEN** the draft revision or current aggregate changes after eligibility is read and before evaluation starts
- **THEN** the established optimistic revision check rejects the stale start
- **AND** no evaluation run or model side effect is created
