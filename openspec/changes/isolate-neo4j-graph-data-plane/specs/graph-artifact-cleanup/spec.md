## ADDED Requirements

### Requirement: Cleanup uses direct graph scope
The system SHALL remove chunks and evidence using copied document, knowledge-base, and extraction-run identifiers without traversing operational metadata roots.

#### Scenario: A document is deleted
- **WHEN** document cleanup runs after relational deletion intent commits
- **THEN** chunks are selected by document ID
- **AND** evidence is selected by source-document ID
- **AND** unrelated graph artifacts remain

#### Scenario: A failed run is cleaned
- **WHEN** PostgreSQL identifies a failed extraction-run ID
- **THEN** only evidence belonging to that run is removed

### Requirement: Canonical facts survive while evidence remains
The system MUST delete an extracted node or relationship only after no retained evidence supports it.

#### Scenario: One of several evidence records is removed
- **WHEN** cleanup removes one source's evidence for a shared fact
- **THEN** the fact and its remaining evidence are preserved

#### Scenario: Last evidence is removed
- **WHEN** cleanup removes the final evidence for a candidate fact
- **THEN** the now-unreferenced extracted fact is eligible for deletion
