## ADDED Requirements

### Requirement: Document artifact cleanup is centralized
The system SHALL provide a centralized cleanup component for document-scoped derived artifacts created during processing and graph extraction.

#### Scenario: Document is replaced
- **WHEN** an existing document is replaced
- **THEN** the cleanup component removes the document's prior chunks, extraction runs, document-scoped graph relationships, and obsolete extracted nodes before the replacement is marked uploaded

#### Scenario: Document is deleted
- **WHEN** an existing document is deleted
- **THEN** the cleanup component removes the document's chunks, extraction runs, document-scoped graph relationships, and obsolete extracted nodes before the document node is deleted

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
