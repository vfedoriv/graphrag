# schema-draft-review Specification

## Purpose
TBD - created by archiving change add-persistent-schema-drafts. Update Purpose after archive.
## Requirements
### Requirement: Evidence origins and review decisions are independent
The system SHALL represent candidate evidence origins as a set of `OBSERVED`, `GUIDED`, `INFERRED`, and `EXISTING` values and SHALL represent user review separately as `PENDING`, `ACCEPTED`, `REJECTED`, `MODIFIED`, or `PINNED`.

#### Scenario: Candidate is guided and observed
- **WHEN** a normalized candidate is both requested by guidance and supported by source evidence
- **THEN** it records both `GUIDED` and `OBSERVED` origins
- **AND** its review state is determined independently

#### Scenario: Inherited candidate enters a base-schema draft
- **WHEN** a draft uses a base schema
- **THEN** each inherited element has `EXISTING` origin and is included in the effective projection
- **AND** removing or incompatibly changing it requires an explicit breaking decision

#### Scenario: Guided candidate has no evidence
- **WHEN** a required guided candidate has no observed evidence
- **THEN** it remains `PENDING` until explicitly accepted, modified, or rejected
- **AND** publication readiness can identify the unresolved decision

### Requirement: Review decisions preserve provenance and survive reanalysis
The system SHALL record decision author-independent timestamps, draft revision, target candidate identity, prior value, resulting value, and optional rationale, and SHALL reapply valid decisions after candidate reanalysis.

#### Scenario: Candidate is accepted or rejected
- **WHEN** a client accepts or rejects a pending candidate using the current draft revision
- **THEN** the decision is persisted and the effective schema projection is recomputed deterministically

#### Scenario: Candidate is modified
- **WHEN** a client replaces a candidate definition with a valid user-edited definition
- **THEN** the system keeps the original evidence-backed candidate and stores the user definition as the effective reviewed result
- **AND** the modification does not fabricate observed evidence

#### Scenario: Candidate is pinned
- **WHEN** a client pins an accepted or modified definition
- **THEN** later analysis cannot silently replace or remove the pinned definition
- **AND** competing evidence is reported as a conflict against the pinned value

#### Scenario: Reanalysis removes support
- **WHEN** a previously accepted candidate loses all active-source evidence after reanalysis
- **THEN** its prior decision remains visible
- **AND** the system reports the evidence loss for review instead of silently deleting the effective definition

### Requirement: Conflicts require explicit resolution
The system SHALL expose type, key, alias, relationship-name, relationship-direction, and pinned-definition conflicts, SHALL require an explicit selected alternative or user-modified resolution before a new or changed conflict is considered resolved, and SHALL preserve a valid explicit resolution when an equivalent conflict recurs in a later current aggregate revision.

#### Scenario: Client selects a conflict alternative
- **WHEN** a client resolves a conflict by selecting one valid alternative
- **THEN** the chosen definition enters the effective projection
- **AND** rejected alternatives and their evidence remain auditable

#### Scenario: Client supplies a custom resolution
- **WHEN** a client resolves a conflict with a valid custom definition
- **THEN** the resolution is recorded as user-modified
- **AND** all original alternatives remain linked to the resolution

#### Scenario: Conflicting key remains unresolved
- **WHEN** no explicit resolution exists for competing node keys
- **THEN** the conflict remains blocking for publication readiness
- **AND** the system does not silently synthesize a composite key

#### Scenario: Equivalent conflict recurs after reanalysis
- **WHEN** the current aggregate contains a conflict whose type, coordinate, and normalized alternative set equal a previously resolved conflict for the same draft
- **AND** the prior selected or custom resolution remains valid for the recurring conflict
- **THEN** the system creates the current aggregate's conflict as resolved with the prior resolution
- **AND** preserves both records with their aggregate lineage for audit

#### Scenario: Conflict alternatives change after reanalysis
- **WHEN** a conflict recurs at the same display coordinate but its type or normalized alternative set differs from the previously resolved conflict
- **THEN** the current aggregate conflict remains unresolved
- **AND** the historical resolution is not silently applied

#### Scenario: Prior custom resolution is no longer valid
- **WHEN** an otherwise equivalent recurring conflict has a prior custom resolution that fails current resolution validation
- **THEN** the current aggregate conflict remains unresolved
- **AND** the invalid prior resolution remains visible only as historical audit data

### Requirement: Draft diffs are deterministic and compatibility-classified
The system SHALL compute a deterministic diff of the effective reviewed projection against an immutable snapshot of the base schema when present, otherwise against the effective aggregate that was current immediately before the present aggregate, and otherwise against an empty schema; SHALL identify the exact baseline type, identifier, and content hash in the response; and SHALL classify each change as additive, review-required, or breaking.

#### Scenario: Optional property is added
- **WHEN** the effective projection adds a new optional property
- **THEN** the diff classifies the change as additive

#### Scenario: Identity key changes
- **WHEN** the effective projection replaces an inherited node identity key
- **THEN** the diff classifies the change as breaking
- **AND** identifies the old and proposed key definitions

#### Scenario: Property type widens
- **WHEN** a reviewed resolution changes a property to a compatible wider type
- **THEN** the diff classifies the change as review-required

#### Scenario: Diff uses a base schema
- **WHEN** a draft has a configured base schema
- **THEN** the diff response identifies `BASE_SCHEMA`, the base schema ID, and the hash of the exact snapshotted comparison content
- **AND** an edit to the schema definition after the snapshot does not silently change that aggregate's diff baseline

#### Scenario: Diff uses the previous current aggregate
- **WHEN** a draft without a base schema promotes a new aggregate while another aggregate is current
- **THEN** the new aggregate snapshots the prior current aggregate's effective projection as its baseline
- **AND** the diff response identifies `PREVIOUS_AGGREGATE`, that prior current aggregate's ID, and the snapshot content hash

#### Scenario: Non-current analysis is retained
- **WHEN** an analysis aggregate is retained for audit without becoming current
- **THEN** it does not become a later diff baseline solely because of its aggregate revision or creation order
- **AND** the next promoted aggregate retains lineage to the aggregate that was actually current immediately before promotion

#### Scenario: First aggregate uses an empty baseline
- **WHEN** a draft without a base schema promotes its first aggregate
- **THEN** the diff response identifies `EMPTY`, returns a null baseline ID, and includes the canonical empty-schema content hash

#### Scenario: Diff response is revision-bound
- **WHEN** a client requests the compatibility diff
- **THEN** the response identifies the current aggregate revision and draft revision used to compute the current reviewed projection
- **AND** includes a typed baseline descriptor containing `type`, `id`, and `contentHash`

#### Scenario: Legacy aggregate lacks a baseline snapshot
- **WHEN** the current aggregate predates persisted baseline metadata
- **THEN** the system deterministically resolves the best available compatible baseline and returns the normal typed descriptor
- **AND** does not overwrite an immutable stored snapshot when one exists

#### Scenario: Diff is requested repeatedly
- **WHEN** clients request a diff for the same aggregate and decision revision
- **THEN** the ordered diff content, classifications, baseline descriptor, and before/after values are identical

### Requirement: Candidate retrieval has an explicit reviewed page contract
The system SHALL return draft candidates in a typed page envelope containing zero-based page number, bounded page size, total element count, and typed candidate content ordered deterministically.

#### Scenario: Client reads a candidate page
- **WHEN** a client requests a valid candidate page for an owned draft with a current aggregate
- **THEN** the response contains `page`, `size`, `totalElements`, and `content`
- **AND** each content item explicitly describes the discovery candidate fields, recommendation state, and evidence

#### Scenario: Candidate has a persisted decision
- **WHEN** a candidate has one or more append-only review decisions
- **THEN** its response contains the effective persistent review state and latest decision identifier
- **AND** the analyzer recommendation remains a separately named field

#### Scenario: Candidate was rejected
- **WHEN** the latest decision rejects an evidence-backed candidate
- **THEN** candidate retrieval still returns the candidate and its evidence
- **AND** its effective persistent review state is `REJECTED`

### Requirement: Conflict retrieval distinguishes current review state from history
The system SHALL scope the default conflict list to the draft's current aggregate revision, SHALL provide an explicit draft-wide history scope, and SHALL identify every returned conflict's aggregate revision and currentness.

#### Scenario: Client opens the Conflicts review tab
- **WHEN** a client lists conflicts without requesting history for a draft with a current aggregate
- **THEN** only conflicts belonging to the current aggregate revision are returned
- **AND** a recurring resolved conflict appears exactly once with its carried resolution state

#### Scenario: Client requests conflict history
- **WHEN** a client explicitly requests draft-wide conflict history
- **THEN** conflicts from current and prior aggregate revisions are returned in deterministic revision and coordinate order
- **AND** every conflict identifies its aggregate revision and whether that aggregate is current

#### Scenario: Draft has no current aggregate
- **WHEN** a client lists current conflicts for a draft that has no current aggregate revision
- **THEN** the system returns an empty conflict collection
- **AND** does not expose historical records as current review work

#### Scenario: Non-current analysis finishes
- **WHEN** an analysis result is retained for audit but does not become the draft's current aggregate
- **THEN** its conflicts do not appear in the default current conflict list
- **AND** they remain available through the explicit history scope
