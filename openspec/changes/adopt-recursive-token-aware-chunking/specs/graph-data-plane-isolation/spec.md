## ADDED Requirements

### Requirement: Revisioned child persistence
Neo4j SHALL persist recursive child fields needed for ordering, filtering, cleanup, retrieval, and provenance as first-class properties while retaining knowledge-base, document, processing-run, and embedding-space isolation.

#### Scenario: Cross-knowledge-base lookup
- **WHEN** a chunk identifier from another knowledge base is supplied to a scoped operation
- **THEN** the operation does not return or mutate that chunk

#### Scenario: Recursive overwrite
- **WHEN** a document is successfully overwritten under a new strategy revision
- **THEN** obsolete document-scoped chunks and dependent graph artifacts from the prior revision are removed
