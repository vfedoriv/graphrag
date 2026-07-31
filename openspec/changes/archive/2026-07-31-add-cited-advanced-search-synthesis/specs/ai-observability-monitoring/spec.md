## ADDED Requirements

### Requirement: Advanced-search observations are complete and privacy controlled
The system SHALL emit one `advanced-search` workflow observation with child observations for planning, retrievers, fusion, expansion, reranking, evaluation, follow-up, synthesis, and repair when those stages execute.

#### Scenario: Content capture is disabled
- **WHEN** an advanced-search run executes with AI content capture disabled
- **THEN** observations and normal logs contain only approved identifiers, fingerprints, counts, timings, statuses, fallback flags, error classes, and token metadata

#### Scenario: Optional retriever fails
- **WHEN** one branch fails but evidence remains usable
- **THEN** metrics record branch latency/failure and the workflow records partial fallback without logging retrieved content

#### Scenario: Synthesis abstains
- **WHEN** citation or sufficiency validation prevents an answer
- **THEN** metrics record abstention, citation counts, repair use, and terminal status
