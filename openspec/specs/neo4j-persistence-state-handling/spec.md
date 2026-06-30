# neo4j-persistence-state-handling Specification

## Purpose
TBD - created by archiving change harden-neo4j-persistence-state-handling. Update Purpose after archive.
## Requirements
### Requirement: Assigned-id Neo4j entities use stable state detection
The system SHALL map backend-owned Neo4j application entities that use assigned business identifiers with Spring Data Neo4j-supported persistence state metadata.

#### Scenario: Assigned-id entity is saved
- **WHEN** the system saves a Neo4j application entity whose identifier is assigned by application code
- **THEN** the entity mapping includes persistence state metadata supported by Spring Data Neo4j
- **AND** normal save operations do not emit assigned-id new-entity warnings

#### Scenario: Entity has a business version field
- **WHEN** a Neo4j application entity already has a business field named `version`
- **THEN** persistence state metadata uses a distinct field name
- **AND** the business version meaning remains unchanged

### Requirement: Repository query contracts avoid projection metadata noise
The system SHALL define custom Neo4j repository query methods with return types that avoid Spring Data projection metadata calculation noise during normal application workflows.

#### Scenario: Custom existence query is invoked
- **WHEN** a custom repository query returns an existence result
- **THEN** the repository contract uses a nullable wrapper return type or another non-primitive type compatible with Spring Data mapping
- **AND** service code handles the result with null-safe truth checks

#### Scenario: Custom write query is invoked
- **WHEN** a custom repository query performs a write, delete, merge, or update operation
- **THEN** the repository contract returns an execution result such as an affected-row count rather than `void`
- **AND** normal repository invocation does not emit primitive or void class metadata warnings

### Requirement: Persistence hardening preserves public behavior
The system SHALL keep persistence state hardening internal to backend mapping and repository contracts.

#### Scenario: Client uses existing API workflows
- **WHEN** clients create, update, retrieve, process, or delete schemas, knowledge bases, documents, AI profiles, runtime settings, chunks, processing runs, or extraction runs
- **THEN** request and response shapes remain unchanged
- **AND** existing business identifiers, graph labels, relationships, statuses, and lifecycle semantics remain unchanged

#### Scenario: Neo4j-backed workflow is exercised
- **WHEN** integration tests exercise document processing, schema, knowledge-base, and AI profile persistence paths
- **THEN** the workflows continue to pass
- **AND** the exercised paths do not log assigned-id new-entity warnings or primitive/void projection metadata warnings

