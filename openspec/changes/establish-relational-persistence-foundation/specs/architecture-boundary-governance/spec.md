## ADDED Requirements

### Requirement: Operational state and graph data have distinct owners
The system SHALL treat PostgreSQL as canonical for application metadata and workflow state and Neo4j as canonical for chunks, embeddings, extracted facts, evidence, provenance, and graph-native relationships.

#### Scenario: A stable identifier crosses the store boundary
- **WHEN** a PostgreSQL-owned document, run, schema, or knowledge-base identifier is written to a graph artifact
- **THEN** the identifier is copied explicitly for graph scoping
- **AND** Neo4j does not become authoritative for the operational record

#### Scenario: A workflow spans both stores
- **WHEN** an operation requires relational and graph mutations
- **THEN** each mutation occurs in its own store-specific transaction
- **AND** no component presents the mutations as atomically committed across both stores
