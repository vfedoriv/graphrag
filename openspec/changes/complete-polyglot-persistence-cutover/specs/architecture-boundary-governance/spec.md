## ADDED Requirements

### Requirement: Retired operational graph structures are prohibited
The final system SHALL NOT define, initialize, or persist Neo4j labels and relationships for PostgreSQL-owned profiles, knowledge bases, schemas, documents, runs, settings, storage mutations, draft workflows, publications, or reprocessing workflows.

#### Scenario: The canonical flow completes
- **WHEN** the end-to-end flow creates and processes GraphRAG application data
- **THEN** Neo4j contains the allowed graph-native artifacts
- **AND** no retired operational label or relationship exists

#### Scenario: Source architecture is checked
- **WHEN** architecture verification scans domain and repository packages
- **THEN** no retired operational SDN entity or repository remains
