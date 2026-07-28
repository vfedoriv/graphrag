## ADDED Requirements

### Requirement: Relational activation preserves one active schema
The system SHALL change knowledge-base schema activation in one relational transaction and SHALL prevent more than one active schema association per knowledge base.

#### Scenario: A different schema is activated
- **WHEN** a valid inactive associated schema is activated
- **THEN** the prior active association is deactivated
- **AND** the selected association becomes active in the same commit

#### Scenario: Concurrent activation occurs
- **WHEN** concurrent requests activate different schemas for the same knowledge base
- **THEN** relational locking or constraints prevent a multiple-active result
