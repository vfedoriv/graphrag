## ADDED Requirements

### Requirement: Draft responses expose lightweight workflow summaries
The system SHALL include nullable current-analysis, latest-evaluation, and latest-reprocessing references in draft detail and list responses without embedding run or plan outcome collections.

#### Scenario: Draft has recoverable workflow state
- **WHEN** a client retrieves or lists a draft with analysis, evaluation, or reprocessing history
- **THEN** the response identifies the applicable current analysis and the latest evaluation and reprocessing resources with their statuses
- **AND** each reference indicates its current or latest semantics explicitly

#### Scenario: Draft has no workflow history
- **WHEN** a draft has no analysis, evaluation, or reprocessing resources
- **THEN** the corresponding workflow references are null

#### Scenario: Latest evaluation became stale
- **WHEN** a draft changes after its latest evaluation was created
- **THEN** the draft response may still reference that latest evaluation
- **AND** the reference reports that the evaluation is not current for the draft

#### Scenario: Knowledge base draft list is read
- **WHEN** a client lists multiple drafts
- **THEN** the system resolves their lightweight workflow references with bounded batch access
- **AND** does not load full source outcomes, evaluation outcomes, or plan items
