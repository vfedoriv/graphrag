## ADDED Requirements

### Requirement: Evaluation, publication, and reprocessing use recoverable checkpoints
The system SHALL commit relational intent and claims before model, schema, graph, or document-processing effects and SHALL commit completion in a later store-specific checkpoint.

#### Scenario: External work fails
- **WHEN** a claimed workflow step fails after its relational checkpoint
- **THEN** retry metadata and ownership remain recoverable
- **AND** no cross-store rollback is assumed

#### Scenario: External work succeeds twice
- **WHEN** recovery repeats an already-successful idempotent effect
- **THEN** uniqueness and stable workflow identity prevent duplicate durable outcomes
