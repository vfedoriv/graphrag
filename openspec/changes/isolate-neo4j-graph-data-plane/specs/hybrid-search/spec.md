## ADDED Requirements

### Requirement: Hybrid search scopes candidates within Neo4j
The system SHALL restrict vector and graph candidates to the requested knowledge base and compatible embedding space before applying the candidate limit.

#### Scenario: Another knowledge base has closer competing vectors
- **WHEN** hybrid search runs for a knowledge base whose vectors compete with closer vectors from another knowledge base
- **THEN** only candidates in the requested knowledge base can consume its result limit

### Requirement: Hybrid search enriches metadata relationally in batches
The system SHALL batch-load PostgreSQL document metadata for Neo4j search hits and SHALL preserve response ordering and current API representations.

#### Scenario: Multiple hits reference documents
- **WHEN** Neo4j returns hits for multiple document IDs
- **THEN** document metadata is retrieved through a bounded batch operation rather than one query per hit
- **AND** the final results preserve their search ranking

#### Scenario: A stale graph hit references missing metadata
- **WHEN** a hit's document no longer exists in PostgreSQL
- **THEN** the hit is omitted or handled through the established stale-artifact policy
- **AND** metadata from another document is never substituted
