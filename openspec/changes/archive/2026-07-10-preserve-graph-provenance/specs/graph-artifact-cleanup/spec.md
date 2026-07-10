## MODIFIED Requirements

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
