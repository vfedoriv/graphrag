# multi-source-graph-provenance Specification

## Purpose
TBD - created by archiving change preserve-graph-provenance. Update Purpose after archive.
## Requirements
### Requirement: Extraction evidence is immutable and source-scoped
The system SHALL persist a distinct evidence record for every extracted node or relationship assertion that identifies its document, chunk, extraction run, schema, and canonical fact identity.

#### Scenario: Two documents assert the same relationship
- **WHEN** two completed extraction runs from different documents assert the same schema-scoped relationship between the same canonical endpoints
- **THEN** the system retains two independently addressable evidence records
- **AND** both records reference the same canonical relationship fact

#### Scenario: Multiple chunks assert the same node
- **WHEN** two chunks in one extraction run assert the same canonical node
- **THEN** the system retains evidence for both chunks
- **AND** the canonical node is not duplicated

### Requirement: Canonical facts survive retained evidence
The system SHALL retain a canonical extracted node or relationship while at least one retained evidence record references it.

#### Scenario: One source is deleted
- **WHEN** a document is deleted and another document retains evidence for the same canonical relationship
- **THEN** the deleted document's evidence is removed
- **AND** the canonical relationship and the retained document's evidence remain available

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
