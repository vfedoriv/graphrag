## ADDED Requirements

### Requirement: Conflicts and decisions are relational workflow history
The system SHALL persist aggregate conflicts and ordered review decisions in PostgreSQL with draft/revision ownership and unique decision sequences.

#### Scenario: A reviewer records a decision
- **WHEN** the target conflict belongs to the current reviewable aggregate
- **THEN** the next decision sequence commits atomically
- **AND** resulting currentness is reflected in subsequent review reads

#### Scenario: Concurrent decisions use the same sequence
- **WHEN** two writers attempt the same next sequence
- **THEN** the database prevents duplicate ordering
- **AND** the losing writer receives a conflict
