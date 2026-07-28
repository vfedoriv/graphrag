## ADDED Requirements

### Requirement: Graph evidence is independent of operational run nodes
The system SHALL create and query extraction evidence using copied run, document, and knowledge-base identifiers while preserving evidence-to-source and evidence-to-fact provenance.

#### Scenario: Evidence is written for a fact
- **WHEN** extraction persists a fact supported by a document chunk
- **THEN** graph evidence links the retained graph artifacts
- **AND** no operational extraction-run or document root is created

#### Scenario: Multiple documents support one fact
- **WHEN** evidence from multiple source documents asserts the same canonical fact
- **THEN** each evidence record retains its own copied source scope
- **AND** the shared fact remains attributable to every surviving source
