## ADDED Requirements

### Requirement: Legacy hybrid settings are retired deterministically
The system SHALL idempotently migrate only persisted hybrid-search overrides with exact advanced-search semantic equivalents, SHALL NOT overwrite explicit advanced-search overrides, and SHALL remove or report obsolete keys without silently reinterpreting them.

#### Scenario: Equivalent advanced override is absent
- **WHEN** a compatible legacy evidence-text or candidate-bound override exists during migration
- **THEN** its value is copied to the documented advanced-search key and the legacy override is retired

#### Scenario: Advanced override already exists
- **WHEN** both compatible legacy and explicit advanced-search overrides exist
- **THEN** the advanced-search value wins and is not overwritten

#### Scenario: Candidate multiplier is persisted
- **WHEN** migration encounters a legacy candidate-multiplier or default-graph-depth override
- **THEN** it is reported and retired without creating a misleading advanced-search value
