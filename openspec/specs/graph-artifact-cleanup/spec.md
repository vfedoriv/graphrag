# graph-artifact-cleanup Specification

## Purpose
Define centralized cleanup behavior for document-scoped derived graph artifacts and extraction-run artifacts.
## Requirements
### Requirement: Document artifact cleanup is centralized
The system SHALL provide a centralized cleanup component for document-scoped derived artifacts created during processing and graph extraction. Cleanup SHALL remove only evidence owned by the target document and SHALL remove a canonical extracted fact only when no retained evidence supports it.

#### Scenario: Document is replaced
- **WHEN** an existing document is replaced
- **THEN** the cleanup component removes the document's prior chunks, extraction runs, document-scoped graph evidence, and obsolete extracted facts before the replacement is marked uploaded

#### Scenario: Document is deleted
- **WHEN** an existing document is deleted
- **THEN** the cleanup component removes the document's chunks, extraction runs, document-scoped graph evidence, and obsolete extracted facts before the document node is deleted

#### Scenario: Document cleanup encounters a shared fact
- **WHEN** a target document has evidence for a canonical node or relationship that another retained document also supports
- **THEN** cleanup removes only the target document's evidence
- **AND** cleanup retains the shared canonical fact

### Requirement: Extraction completion cleanup is centralized
The system SHALL provide centralized cleanup for obsolete extraction runs after a successful extraction completes.

#### Scenario: Successful retry after failed runs
- **WHEN** a document extraction completes after previous failed runs
- **THEN** the cleanup component removes failed runs and their document-scoped graph artifacts while preserving the current completed run

#### Scenario: Overwrite successful completed run
- **WHEN** a document extraction completes with overwrite enabled
- **THEN** the cleanup component removes prior completed runs and their document-scoped graph artifacts while preserving the current completed run

### Requirement: Cleanup reports deletion counts
The cleanup component SHALL return deletion counts for each artifact category it mutates so callers can log and test cleanup behavior without parsing Cypher internals.

#### Scenario: Cleanup completes
- **WHEN** document artifact cleanup or extraction completion cleanup finishes
- **THEN** the caller receives counts for deleted runs, deleted relationships, and deleted obsolete extracted nodes, plus deleted chunk counts when chunks are part of the cleanup

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

### Requirement: Hierarchy-aware graph cleanup
Document cleanup SHALL remove graph evidence and relationships that reference any parent or child before removing the complete document-scoped chunk hierarchy and obsolete extracted nodes.

#### Scenario: Document deletion
- **WHEN** an owned document with parent-child chunks is deleted
- **THEN** no parent, child, embedding, extraction evidence, document-scoped relationship, or obsolete extracted node remains
