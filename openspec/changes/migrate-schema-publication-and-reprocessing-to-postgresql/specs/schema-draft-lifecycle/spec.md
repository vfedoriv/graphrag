## ADDED Requirements

### Requirement: Complete draft workflow summaries are relational
The system SHALL derive draft navigation summaries from relational analysis, evaluation, publication, and reprocessing state with revision currentness and ownership safety.

#### Scenario: A published draft is retrieved
- **WHEN** a draft has evaluation, publication, and reprocessing history
- **THEN** its detail response reports current lifecycle summaries through bounded relational projections
- **AND** historical non-current records do not replace the current revision summary
