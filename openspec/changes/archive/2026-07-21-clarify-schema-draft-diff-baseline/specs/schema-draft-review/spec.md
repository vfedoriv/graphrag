## MODIFIED Requirements

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
