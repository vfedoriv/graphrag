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

### Requirement: Knowledge-base lifecycle respects document ownership
Architecture verification SHALL require knowledge-base lifecycle document-state inspection and artifact cleanup to use knowledge-base-owned ports implemented through document public capabilities. Document repositories, persistence records, and cleanup implementations SHALL remain behind document-owned capabilities; lifecycle admission and sequencing SHALL remain knowledge-base-owned.

#### Scenario: Lifecycle directly accesses document internals
- **WHEN** verification finds a migrated knowledge-base lifecycle dependency on document repositories, persistence records, or artifact cleanup implementations
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Lifecycle requests owned facts and cleanup
- **WHEN** knowledge-base lifecycle consumes an owned-document count or requests scoped artifact cleanup through its ports
- **THEN** verification permits the public immutable contract dependency
- **AND** the integration implementation accesses only document public capabilities rather than repositories or cleanup implementations

### Requirement: AI embedding compatibility separates observations from rules
Architecture verification SHALL require stored embedding observations to enter AI compatibility through an AI-owned port backed by a document public capability. Compatibility rules SHALL operate on immutable non-secret target and stored-observation values without document persistence types, repository access, external clients, or application assembly dependencies.

#### Scenario: Compatibility reads stored chunk records directly
- **WHEN** verification finds document repository or persistence-record dependencies in migrated AI compatibility code
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Deterministic comparison uses boundary values
- **WHEN** compatibility compares a target embedding descriptor with stored observations supplied through its port
- **THEN** verification permits the immutable value dependencies
- **AND** rejects persistence or external-client dependencies in the deterministic rule

### Requirement: AI profile management respects knowledge-base assignment ownership
Architecture verification SHALL require AI profile update and deletion assignment inspection to use an AI-owned port implemented through a knowledge-base public capability. AI profile persistence SHALL NOT access knowledge-base repositories or persistence records; knowledge bases SHALL retain ownership of profile associations.

#### Scenario: AI profile persistence reads foreign assignment state
- **WHEN** verification finds knowledge-base repository or persistence-record dependencies in AI profile persistence
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Profile admission consumes assignment facts
- **WHEN** AI profile management checks assignment presence or assigned knowledge-base identities through its port
- **THEN** verification permits the public contract dependency
- **AND** knowledge-base persistence stays behind its public capability

### Requirement: Knowledge-base and AI integration boundaries are scoped and enforced
Architecture verification SHALL constrain the migrated integration adapters to consumer-owned ports, provider public capabilities, immutable boundary values, and application wiring support. Adapters SHALL NOT access feature repositories, persist state, or own lifecycle or compatibility decisions. Production features SHALL NOT depend on assembly implementations. Any transitional exceptions outside this migrated slice SHALL be frozen by explicit class/path with an identified retirement slice and SHALL NOT permit the foreign state dependencies removed by this change.

#### Scenario: An integration adapter bypasses a public capability
- **WHEN** verification finds a migrated integration adapter dependency on feature repositories, persistence records, or implementation services
- **THEN** verification fails and identifies the bypass

#### Scenario: A feature depends on application assembly
- **WHEN** verification finds a production feature dependency on a migrated integration adapter implementation
- **THEN** verification fails and identifies the dependency

#### Scenario: A transitional exception expands
- **WHEN** a new caller or forbidden dependency attempts to use a named transitional exception outside its frozen scope
- **THEN** verification rejects the dependency
- **AND** completed reprocessing isolation remains enforced without preparation exceptions

### Requirement: Consolidated documents has enforceable internal ownership
Architecture verification SHALL treat document management, processing, extraction, run history and recovery, storage reconciliation, parsing/chunking, and document-owned relational and graph adapters as document internals. Foreign feature access SHALL use public capabilities and immutable boundary values, except for exact pre-existing dependencies explicitly frozen with a retirement roadmap step. Consolidation SHALL preserve all ownership restrictions established by reprocessing, knowledge-base lifecycle, and AI state isolation.

#### Scenario: A new foreign caller accesses document internals
- **WHEN** a production feature outside documents introduces direct access to document persistence records, repository ports, application implementations, or effect adapters
- **THEN** architecture verification fails and identifies the dependency
- **AND** an exception for another caller does not authorize the new access

#### Scenario: An established public capability is consumed
- **WHEN** an integration adapter accesses documents through the public reprocessing, preparation, outcome, lifecycle, or stored-embedding capability
- **THEN** architecture verification accepts the public capability and immutable value dependencies
- **AND** rejects bypasses to document internals

### Requirement: Deterministic document rules are isolated from external effects
Architecture verification SHALL reject persistence, filesystem, model-client, application-workflow, and assembly dependencies in deterministic document rules. Document workflows SHALL invoke external effects through owned ports or governed adapters rather than directly using external clients. Typed settings and observability access SHALL remain governed support dependencies.

#### Scenario: A pure chunk planning rule acquires a client
- **WHEN** a deterministic document rule depends on a database, filesystem, or model client
- **THEN** architecture verification rejects the dependency

#### Scenario: A workflow persists an embedding
- **WHEN** a document workflow invokes its governed persistence adapter or owned port
- **THEN** verification accepts that boundary
- **AND** rejects a direct external-client dependency in the workflow

### Requirement: Document compatibility callers use AI ownership
Architecture verification SHALL require document processing and migration preparation to invoke AI-owned embedding compatibility through non-secret boundary values. Documents SHALL NOT use the transitional compatibility bridge retained for unmigrated search callers or duplicate AI compatibility rules.

#### Scenario: A document compatibility check uses the legacy bridge
- **WHEN** verification finds document processing or preparation depending on the transitional compatibility bridge
- **THEN** verification fails

#### Scenario: Stored observations enter compatibility
- **WHEN** AI reads stored observations through its port and the documents public capability
- **THEN** verification preserves the existing observation-versus-rule ownership boundary

### Requirement: Document consolidation exceptions cannot expand
Architecture verification SHALL freeze transitional foreign callers and document dependencies on unmigrated schema, knowledge-base, AI, settings, or support implementations by exact originating class and target dependency, with an assigned retirement step. Exceptions SHALL NOT permit feature dependencies on assembly, secret-bearing public values, new callers, or removed reprocessing/knowledge-base/AI state bypasses.

#### Scenario: A deferred schema dependency remains
- **WHEN** an explicitly frozen document schema-resolution dependency remains pending registry migration
- **THEN** verification accepts only its recorded class/dependency pair
- **AND** identifies registry migration as its retirement step

#### Scenario: An exception grows
- **WHEN** a caller adds an unlisted target dependency or a different caller attempts the same access
- **THEN** verification fails

### Requirement: Schema registry and discovery own their implementations
Architecture verification SHALL restrict foreign access to schema registry and discovery persistence, workflows, and adapters to public contracts, except for exact pre-existing compatibility dependencies assigned to later roadmap steps. Schema public snapshots SHALL be immutable and free of persistence records, external clients, and secrets. Document extraction SHALL consume public schema resolution rather than schema repositories or implementation services.

#### Scenario: A foreign workflow reads a schema repository
- **WHEN** a migrated document extraction workflow or new foreign caller directly accesses schema persistence or registry implementation
- **THEN** architecture verification fails

#### Scenario: A workflow consumes a snapshot
- **WHEN** a foreign workflow consumes complete immutable schema values through public resolution
- **THEN** verification accepts the public contract dependency

### Requirement: Discovery input preparation respects document ownership
Architecture verification SHALL require synchronous schema discovery to obtain owned document input and request-file parsing through schemas-owned consumer ports implemented using documents public capabilities. Documents SHALL own source access and parsing; schemas SHALL retain discovery-specific bounds, ordering, fingerprints, analysis chunk construction, guidance, model-result handling, aggregation, and review-only policy.

#### Scenario: Discovery accesses document storage directly
- **WHEN** verification finds synchronous discovery depending on document repositories, persistence records, storage implementations, or parser implementations
- **THEN** verification fails

#### Scenario: Discovery uses prepared input
- **WHEN** discovery receives document identity, source byte count, fingerprint inputs, and parsed text through its input port
- **THEN** verification accepts the immutable input contract
- **AND** focused boundary verification confirms preparation performs no document processing, graph writes, or durable storage of request-only sources

### Requirement: Registry uses knowledge-base-owned association capabilities
Architecture verification SHALL require schema registry and active-schema resolution to inspect knowledge-base existence and associations, provision knowledge bases, and mutate schema associations through schemas-owned consumer ports backed by knowledge-base public capabilities. Schema definition persistence SHALL NOT directly access knowledge-base records or association repositories. Schema identity validation and activation admission SHALL remain schema-owned; association state and locking SHALL remain knowledge-base-owned.

#### Scenario: Schema persistence accesses foreign association state
- **WHEN** verification finds schema definition persistence directly accessing knowledge-base or association persistence
- **THEN** verification fails

#### Scenario: Activation uses its association capability
- **WHEN** registry validates a schema and requests association activation through its port
- **THEN** verification accepts public contract access
- **AND** focused verification confirms association locking and mutation join the caller's relational transaction

### Requirement: Registry and discovery integration remains mapping-only
Architecture verification SHALL constrain registry/discovery integration adapters to consumer ports, provider public capabilities, immutable values, and wiring support. Adapters SHALL NOT access repositories, own schema/discovery policy, add transaction boundaries, or be dependencies of production features. Completed registry/discovery migration SHALL remove its corresponding document consolidation exceptions; remaining draft, publication, reprocessing, and search compatibility dependencies SHALL be frozen by exact class and target with their retirement step.

#### Scenario: An integration adapter bypasses a capability
- **WHEN** an integration adapter accesses persistence, implements discovery limits or activation policy, or adds a transaction boundary
- **THEN** boundary verification rejects the bypass

#### Scenario: A completed boundary retains an exception
- **WHEN** synchronous discovery still directly accesses document internals or document extraction still accesses the legacy schema implementation after this migration
- **THEN** verification fails without permitting the former transitional exception

#### Scenario: A later-slice caller expands its bridge access
- **WHEN** a new caller or dependency attempts to use a frozen legacy schema/discovery compatibility bridge
- **THEN** verification fails
