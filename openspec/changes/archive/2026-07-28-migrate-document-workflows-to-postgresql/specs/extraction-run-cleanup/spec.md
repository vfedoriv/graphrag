## ADDED Requirements

### Requirement: Extraction cleanup uses relational run authority
The system SHALL resolve failed, stale, replaced, and current extraction run identifiers from PostgreSQL and SHALL remove graph evidence and artifacts by stable copied identifiers.

#### Scenario: A failed retry left partial evidence
- **WHEN** recovery identifies a failed extraction run
- **THEN** graph cleanup removes evidence scoped to that run ID
- **AND** evidence and facts belonging only to unrelated runs remain

#### Scenario: A completed extraction is overwritten
- **WHEN** the replacement run completes successfully
- **THEN** prior completed run state and graph evidence are retired according to existing overwrite semantics
