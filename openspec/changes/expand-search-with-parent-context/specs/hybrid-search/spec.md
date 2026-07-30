## ADDED Requirements

### Requirement: Separate hybrid evidence and context
Hybrid search SHALL preserve each child hit's ID, source text, source span, score, and channel diagnostics separately from optional expanded context ID, text, and range.

#### Scenario: Expanded hybrid hit
- **WHEN** parent expansion succeeds for a hybrid candidate
- **THEN** the response identifies the child as retrieval evidence and the parent as non-ranking context

#### Scenario: Expansion disabled
- **WHEN** expansion is disabled or no valid parent fits the budget
- **THEN** existing child-only hybrid-search behavior remains available
