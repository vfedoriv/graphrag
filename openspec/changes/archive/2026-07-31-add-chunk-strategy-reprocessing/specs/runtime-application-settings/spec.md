## ADDED Requirements

### Requirement: Chunk migration lifecycle reporting
Runtime settings reads SHALL report the effective chunker revision and state that older documents require explicit reprocessing; updating a chunking setting SHALL never create a migration plan automatically.

#### Scenario: Chunk setting updated
- **WHEN** an operator saves a valid behavior-affecting chunk setting
- **THEN** the response exposes the new effective revision and explicit migration requirement without queuing work
