## ADDED Requirements

### Requirement: Knowledge-base schema listings use relational ownership
The system SHALL list only schema definitions associated with the requested knowledge base through relational ownership and preserve existing sorting, pagination, content inclusion, and not-found behavior.

#### Scenario: Associated schemas are listed
- **WHEN** a caller lists schemas for an existing knowledge base
- **THEN** only its relationally associated schemas are returned
- **AND** active-state projection remains correct
