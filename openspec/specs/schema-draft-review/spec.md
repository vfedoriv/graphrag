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
The system SHALL expose type, key, alias, relationship-name, relationship-direction, and pinned-definition conflicts and SHALL require an explicit selected alternative or user-modified resolution before the conflict is considered resolved.

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

### Requirement: Draft diffs are deterministic and compatibility-classified
The system SHALL compute a deterministic diff of the effective reviewed projection against the base schema when present and against the prior effective aggregate revision, classifying each change as additive, review-required, or breaking.

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

#### Scenario: Diff is requested repeatedly
- **WHEN** clients request a diff for the same aggregate and decision revision
- **THEN** the ordered diff content and classifications are identical

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
