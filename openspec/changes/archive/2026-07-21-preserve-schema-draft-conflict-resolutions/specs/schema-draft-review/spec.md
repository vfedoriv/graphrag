## MODIFIED Requirements

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

## ADDED Requirements

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

