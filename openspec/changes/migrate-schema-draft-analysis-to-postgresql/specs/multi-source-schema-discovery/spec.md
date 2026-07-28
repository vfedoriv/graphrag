## ADDED Requirements

### Requirement: Discovery workflow state is relational
The system SHALL persist bounded source analysis results, deterministic aggregate revisions, conflicts, and review decisions in PostgreSQL without changing the review-only discovery projection.

#### Scenario: Multiple sources are analyzed
- **WHEN** owned eligible sources complete analysis
- **THEN** their results aggregate deterministically into a relational revision
- **AND** the generated schema remains review-only until explicit publication

#### Scenario: Inputs and guidance are unchanged
- **WHEN** an identical eligible source revision set and guidance are analyzed
- **THEN** existing deterministic reuse behavior is preserved
