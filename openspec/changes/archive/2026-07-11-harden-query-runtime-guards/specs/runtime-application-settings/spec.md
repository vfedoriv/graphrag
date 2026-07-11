## ADDED Requirements

### Requirement: Query safety settings describe effective execution behavior
The system SHALL apply and report live query maximum-row and timeout settings consistently across query validation, generation, ask, and execution.

#### Scenario: Live query timeout changes
- **WHEN** a client changes the live query timeout setting
- **THEN** subsequent planner validation and query execution use the updated timeout
- **AND** all corresponding query responses report the updated effective timeout

#### Scenario: Query settings response is generated
- **WHEN** a query API response includes maximum rows or timeout metadata
- **THEN** the values come from the same runtime policy snapshot used to process that request
