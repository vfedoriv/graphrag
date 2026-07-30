## ADDED Requirements

### Requirement: Recursive default lifecycle
The runtime settings API SHALL report recursive chunking as the default for subsequent processing, expose the effective strategy and representation revision, and state whether older processed documents require explicit migration.

#### Scenario: Default strategy activated
- **WHEN** the recursive strategy is deployed as the effective default
- **THEN** new processing uses it while settings reads identify that no automatic corpus reprocessing was started
