# architecture-boundary-governance Specification

## Purpose
TBD - created by archiving change add-architecture-boundary-tests. Update Purpose after archive.
## Requirements
### Requirement: Architecture rules run with the test suite
The system SHALL include automated architecture boundary tests that run as part of the normal Maven test suite.

#### Scenario: Maven tests run
- **WHEN** the normal test command runs
- **THEN** architecture boundary tests execute with the rest of the test suite

### Requirement: Controller boundaries are enforced
Architecture tests SHALL prevent controllers from directly depending on repositories or direct Neo4j database clients.

#### Scenario: Controller dependency is checked
- **WHEN** architecture tests inspect controller classes
- **THEN** any direct dependency from controllers to repository classes or `Neo4jClient` fails the architecture test

### Requirement: Domain boundaries are enforced
Architecture tests SHALL prevent persistence/domain node classes from depending on controllers, services, DTOs, repositories, or application workflow components.

#### Scenario: Domain dependency is checked
- **WHEN** architecture tests inspect domain classes
- **THEN** dependencies from domain classes to controller, service, DTO, repository, or workflow packages fail the architecture test

### Requirement: Direct Neo4j access is governed
Architecture tests SHALL document and constrain which application or persistence components may use `Neo4jClient` directly.

#### Scenario: Direct Neo4j dependency is checked
- **WHEN** architecture tests inspect production classes
- **THEN** direct `Neo4jClient` usage outside the approved allowlist fails the architecture test

### Requirement: Transitional exceptions are explicit
Architecture tests SHALL keep any current exceptions to the target architecture explicit and named so they can be reviewed and removed over time.

#### Scenario: Existing exception is required
- **WHEN** a current dependency does not yet match the target boundary
- **THEN** the architecture test documents the exception by class or package rather than silently allowing the dependency pattern everywhere

### Requirement: Workflow and infrastructure adapter boundaries are enforced
Architecture tests SHALL constrain application workflow components to orchestration and named infrastructure adapters to direct repository, Neo4j, filesystem, or model-client integration.

For document processing, runtime settings, and schema generation, direct infrastructure calls introduced or migrated by this change SHALL reside in the governed infrastructure adapter packages. Unrelated legacy direct Neo4j users SHALL remain explicit frozen exceptions and SHALL NOT be treated as authorization for new direct access.

#### Scenario: Direct infrastructure dependency is introduced
- **WHEN** a workflow component introduces a direct `Neo4jClient`, filesystem, or provider-client dependency outside an approved adapter boundary
- **THEN** the architecture test fails with the originating class and forbidden dependency

#### Scenario: Persistence adapter is introduced
- **WHEN** a named persistence adapter requires direct `Neo4jClient` access
- **THEN** architecture governance permits the dependency through a package-level rule or explicit named exception
- **AND** the exception remains visible to review

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

### Requirement: Retired operational graph structures are prohibited
The final system SHALL NOT define, initialize, or persist Neo4j labels and relationships for PostgreSQL-owned profiles, knowledge bases, schemas, documents, runs, settings, storage mutations, draft workflows, publications, or reprocessing workflows.

#### Scenario: The canonical flow completes
- **WHEN** the end-to-end flow creates and processes GraphRAG application data
- **THEN** Neo4j contains the allowed graph-native artifacts
- **AND** no retired operational label or relationship exists

#### Scenario: Source architecture is checked
- **WHEN** architecture verification scans domain and repository packages
- **THEN** no retired operational SDN entity or repository remains

### Requirement: Reprocessing execution and recovery respect document ownership
Architecture verification SHALL require schema-owned reprocessing execution and
document-outcome inspection to use consumer-owned ports. Document execution,
source inspection, and processing-run retrieval SHALL reside behind
document-owned public capabilities. Integration adapters SHALL translate between
those contracts without introducing a schemas-to-documents implementation
dependency or a feature-to-assembly dependency.

#### Scenario: A migrated execution component accesses document internals
- **WHEN** architecture verification finds a dependency from migrated reprocessing execution or recovery code to document repositories, persistence records, processing stages, or document implementation services
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Integration connects the features
- **WHEN** an application integration adapter implements a schema-owned execution or outcome port
- **THEN** it may use document public capabilities and boundary values
- **AND** neither feature depends on the integration adapter implementation

### Requirement: Reprocessing preparation exceptions are bounded during migration
Architecture verification SHALL keep preparation dependencies remaining outside
the migrated execution/recovery components explicitly named and frozen. An
exception for preparation SHALL NOT authorize document-internal dependencies in
execution or recovery, new callers, or additional preparation dependencies.

#### Scenario: Existing preparation access remains temporarily
- **WHEN** verification encounters a named pre-existing dependency used only by reprocessing input preparation
- **THEN** it is accepted as a documented transitional exception with its removal assigned to the preparation isolation change

#### Scenario: A transitional dependency expands
- **WHEN** a new dependency or migrated execution/recovery caller attempts to use that exception
- **THEN** architecture verification fails

### Requirement: Reprocessing preparation respects document ownership
Architecture verification SHALL require schema-owned reprocessing preparation,
retry preparation, and document-specific target inspection to use consumer-owned
ports implemented through document public capabilities. Document repository,
processing-option, chunk-classification, and runtime chunker dependencies SHALL
remain behind document-owned implementations.

#### Scenario: A preparation workflow accesses document internals
- **WHEN** architecture verification finds a dependency from schema-owned reprocessing preparation or target inspection to document repositories, persistence records, option resolvers, processing stages, or chunker implementation services
- **THEN** verification fails and identifies the originating dependency

#### Scenario: A plan consumes prepared document values
- **WHEN** a schema-owned plan workflow consumes prepared document identities, source hashes, classification, and target values through its port
- **THEN** verification permits the public immutable contract dependency
- **AND** plan claims, selection policy, schema target decisions, and plan persistence remain schema-owned

### Requirement: Completed reprocessing isolation removes preparation exceptions
Architecture verification SHALL remove the reprocessing preparation exceptions
retained during execution/recovery isolation. Integration adapters SHALL map
consumer and provider contracts without depending on either feature's
repositories or moving business workflow ownership into application assembly.

#### Scenario: A prior preparation exception remains after migration
- **WHEN** architecture verification evaluates completed reprocessing isolation
- **THEN** no preparation exception permits access to document internals from schema-owned reprocessing code

#### Scenario: An integration adapter bypasses public capabilities
- **WHEN** an integration adapter directly accesses a document repository or implements document classification or schema plan policy
- **THEN** boundary verification rejects the adapter dependency or focused adapter verification identifies the misplaced behavior
