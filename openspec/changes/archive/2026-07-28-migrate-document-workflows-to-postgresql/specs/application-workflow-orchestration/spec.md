## ADDED Requirements

### Requirement: Document cross-store workflows use durable checkpoints
The system SHALL commit relational intent or run state before filesystem or Neo4j work and SHALL commit relational completion only after the external operation succeeds.

#### Scenario: External work fails after intent commit
- **WHEN** a filesystem or graph operation fails
- **THEN** PostgreSQL retains sufficient state for bounded retry or reconciliation
- **AND** no distributed transaction rollback is assumed

#### Scenario: Completion commit fails after external success
- **WHEN** idempotent external work succeeds but the relational completion checkpoint fails
- **THEN** recovery can recognize or safely repeat the external work
- **AND** eventually commit a consistent terminal state
