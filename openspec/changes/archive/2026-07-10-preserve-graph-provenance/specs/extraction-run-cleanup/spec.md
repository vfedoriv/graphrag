## MODIFIED Requirements

### Requirement: Graph relationships from deleted runs are removed
The system SHALL remove extraction evidence for graph relationships produced by extraction runs deleted during cleanup and SHALL retain a canonical graph relationship when retained evidence from another run or document still supports it.

#### Scenario: Deleted run has domain relationship
- **WHEN** an extraction run is deleted during cleanup and it owns evidence for a graph relationship
- **THEN** that extraction evidence is deleted as part of cleanup

#### Scenario: Retained run has domain relationship
- **WHEN** a canonical graph relationship has evidence for the retained current extraction run
- **THEN** cleanup does not delete the canonical relationship because of stale-run cleanup

#### Scenario: Another document has domain relationship evidence
- **WHEN** a stale run is deleted and another document retains evidence for the same canonical graph relationship
- **THEN** cleanup removes the stale run's evidence
- **AND** the canonical relationship and the other document's evidence remain
