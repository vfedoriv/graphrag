## ADDED Requirements

### Requirement: Neo4j stores only graph-native data
The system SHALL use Neo4j for document chunks, embeddings, extracted schema-defined facts, extraction evidence, provenance, and graph-native relationships and SHALL NOT require operational metadata nodes to read or write those artifacts.

#### Scenario: Extraction persists graph artifacts
- **WHEN** a validated extraction result is written
- **THEN** facts and evidence are persisted without matching an operational knowledge-base, document, schema, processing-run, or extraction-run node

### Requirement: Every retained graph artifact is directly scoped
The system MUST store authoritative copied scope identifiers on chunks and evidence and MUST require those identifiers at graph write boundaries.

#### Scenario: A chunk is persisted
- **WHEN** processing writes a document chunk
- **THEN** the chunk contains its knowledge-base ID and document ID

#### Scenario: Evidence is persisted
- **WHEN** extraction writes evidence
- **THEN** the evidence contains its knowledge-base ID, source-document ID, and extraction-run ID

#### Scenario: Required scope is missing
- **WHEN** a graph write lacks a required authoritative scope identifier
- **THEN** the write is rejected before an unscoped graph artifact is created

### Requirement: Neo4j schema objects serve graph data only
The system SHALL maintain constraints and indexes for graph identity, scope, provenance, and vector retrieval and SHALL retire operational metadata schema objects after their callers are removed.

#### Scenario: Graph schema initialization completes
- **WHEN** Neo4j is initialized for the final graph model
- **THEN** chunk, evidence, scope, provenance, and vector indexes are available
- **AND** no new operational metadata constraint is created
