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

### Requirement: Schema draft authoring owns its state and workflows
Architecture verification SHALL place draft lifecycle, sources, durable analysis, review decisions, conflicts, recovery, and their persistence behind the schemas feature boundary. Other features SHALL consume purpose-specific public contracts rather than draft records, repositories, or workflow implementations.

#### Scenario: A foreign feature accesses draft internals
- **WHEN** a production feature outside schemas depends on a draft persistence record, repository, or authoring workflow implementation
- **THEN** architecture verification fails and identifies the dependency

#### Scenario: A downstream schema workflow needs draft state
- **WHEN** evaluation or publication needs draft ownership, revision, projection, or history facts
- **THEN** it obtains those facts through schema-owned contracts or internal schema interfaces appropriate to its roadmap slice
- **AND** draft persistence remains owned by schema authoring

### Requirement: Draft authoring respects document and registry ownership
Architecture verification SHALL require draft source and analysis workflows to acquire document state, content, and parsing through document public capabilities and schemas-owned consumer ports. Draft lifecycle and review SHALL obtain schema definition and association facts through public schema and knowledge-base capabilities. Boundary values SHALL be immutable and SHALL NOT expose document or registry persistence records, local paths, provider clients, or secrets.

#### Scenario: A draft source reads a document implementation
- **WHEN** a migrated draft authoring component directly accesses a document repository, record, upload service, or parser implementation
- **THEN** architecture verification rejects that dependency

#### Scenario: A draft uses a referenced document snapshot
- **WHEN** draft authoring inspects an owned referenced document's fingerprint or prepares its content
- **THEN** the document capability supplies scoped facts and content through immutable values
- **AND** schemas retains source revision, stale/unavailable classification, analysis limits, and run policy

#### Scenario: A draft checks its base schema
- **WHEN** draft lifecycle or review needs an associated base schema or comparison snapshot
- **THEN** it uses public registry and knowledge-base facts containing the stored identity and content hash
- **AND** it does not depend on registry repositories, mutable records, or parser implementations

### Requirement: Draft integration and transitional dependencies are bounded
Architecture verification SHALL constrain draft integration adapters to consumer-owned ports, provider public capabilities, immutable mapping, and application wiring. Adapters SHALL NOT add transactions or own draft, document, registry, or knowledge-base policy. Completed draft-authoring migration SHALL remove the exact step-6 document and schema bridge exceptions; later evaluation/publication, search, and support exceptions SHALL remain exact and assigned to their roadmap steps.

#### Scenario: An adapter implements draft policy
- **WHEN** an integration adapter classifies source staleness, chooses an analysis retry, changes a review decision, or starts a transaction
- **THEN** boundary verification rejects the misplaced responsibility

#### Scenario: A retired authoring exception remains
- **WHEN** architecture verification scans migrated draft source, lifecycle, analysis, review, or navigation components
- **THEN** no step-6 exception permits access to document, registry, or knowledge-base implementations
- **AND** a new caller cannot use a later-slice exception

### Requirement: Schema evaluation owns policy and consumes document capabilities
Architecture verification SHALL require schema evaluation to own held-out eligibility, revision-specific snapshots, durable claims and outcomes, reuse, deterministic metrics, and advisory interpretation. Owned document inventory, source inspection, preparation, and dry extraction SHALL enter through schemas-owned consumer ports backed by document public capabilities. Evaluation SHALL NOT depend on document repositories, persistence records, storage paths, parser/chunker implementations, extraction clients, or validation implementations.

#### Scenario: Evaluation bypasses document ownership
- **WHEN** architecture verification finds a schema evaluation dependency on document persistence or preparation/extraction implementations
- **THEN** verification rejects the dependency and identifies its origin

#### Scenario: Eligibility consumes scoped document facts
- **WHEN** evaluation lists or inspects held-out documents through its document port
- **THEN** it receives immutable scoped metadata and fingerprints
- **AND** schemas applies the existing successful-contributor hash exclusions, historical document fallback, analysis-readiness checks, and optimistic revision/aggregate checks

#### Scenario: A metric rule depends on an external effect
- **WHEN** architecture verification finds persistence, filesystem, model-client, workflow, or assembly dependencies in deterministic evaluation metric rules
- **THEN** verification rejects the dependency

### Requirement: Dry extraction has an enforceable effect boundary
Document dry-extraction capabilities SHALL prepare and extract through document-owned mechanics and SHALL return immutable observations sufficient to preserve raw-versus-validated metrics. They SHALL NOT create document processing or extraction runs, persist chunks or embeddings, write graph facts or relationships, or mutate draft authoring state. Public values SHALL NOT expose document implementation types, provider clients, credentials, or storage paths.

#### Scenario: A held-out document is evaluated
- **WHEN** evaluation requests preparation and dry extraction under its captured schema and profile inputs
- **THEN** focused boundary verification confirms only source preparation, model extraction, and validation occur
- **AND** document run state, stored chunks/embeddings, graph facts, and draft revisions remain unchanged

#### Scenario: Raw observations contain schema violations
- **WHEN** dry extraction returns unknown labels, invalid relationships, or invalid properties
- **THEN** immutable raw and validated observations retain the distinctions required by the existing metric formulas
- **AND** no invalid observation is persisted in the knowledge-base graph

### Requirement: Publication and reprocessing respect registry ownership
Architecture verification SHALL require schema publication and reprocessing to use schema-owned registry contracts for definition validation, identity/association lookup, inactive registration, and stored or active schema facts. Registry persistence and mutable records SHALL remain registry-owned. Publication SHALL own revision-specific readiness and durable intent/completion; reprocessing SHALL own plan policy, claims, retries, target guards, and recovery.

#### Scenario: Publication reads registry persistence directly
- **WHEN** verification finds publication or reprocessing directly depending on registry repositories or mutable records
- **THEN** verification rejects the dependency

#### Scenario: Publication resumes after registry creation
- **WHEN** registry creation succeeded but publication completion was interrupted
- **THEN** the registry contract permits resolving the same associated identity and stored content hash
- **AND** publication preserves exact intent checks and idempotent completion without duplicate registration or automatic activation/reprocessing

#### Scenario: Reprocessing checks a captured target
- **WHEN** a plan inspects the current schema, knowledge-base association, or profile facts
- **THEN** it consumes immutable non-secret facts through owned contracts
- **AND** preserves the established target guards, all-owned classification scope, snapshots, and document-outcome recovery predicate

### Requirement: Schema workflow checkpoints and summaries retain owned boundaries
Evaluation, publication, and reprocessing SHALL own their persistence and relational checkpoints. Draft authoring SHALL expose purpose-specific internal schema interfaces for downstream authoring facts and publication completion rather than exporting repositories. Draft navigation SHALL obtain downstream evaluation/reprocessing summaries through bounded batch interfaces without directly accessing their persistence. These boundaries SHALL preserve caller transaction participation, independently committed checkpoints, existing currentness and retryability, deterministic ordering, and historical readability.

#### Scenario: Draft navigation reads downstream persistence
- **WHEN** architecture verification finds draft navigation depending on evaluation or reprocessing repositories or persistence records
- **THEN** verification rejects the dependency

#### Scenario: Multiple drafts are listed
- **WHEN** draft navigation composes downstream workflow references for a bounded draft page
- **THEN** it uses batch summary operations rather than one downstream repository query per draft
- **AND** preserves existing latest/current/retryable classifications and response shapes

#### Scenario: A checkpoint completes publication
- **WHEN** publication completion records its result and marks the draft published
- **THEN** both relational changes retain the established completion transaction
- **AND** registry registration and resumable intent/completion retain their established ordering

### Requirement: Step-seven integration and exception retirement are exact
Architecture verification SHALL constrain migrated integration adapters to consumer ports, provider public capabilities, immutable mapping, and assembly support. They SHALL NOT implement eligibility, metric, publication, or plan policy, access repositories, add transactions, or become dependencies of production features. Completed step-seven migration SHALL remove its exact document, registry, and downstream ownership exceptions while retaining only named later search/support/assembly dependencies with assigned retirement steps.

#### Scenario: An integration adapter takes ownership of policy
- **WHEN** an adapter applies held-out eligibility, calculates evaluation metrics, decides publication readiness, changes plan selection, accesses persistence, or starts a transaction
- **THEN** boundary verification rejects the misplaced responsibility

#### Scenario: A retired step-seven edge remains
- **WHEN** verification scans the migrated evaluation, publication, reprocessing, checkpoint, and navigation areas
- **THEN** no step-seven exception permits the removed implementation dependency
- **AND** prior reprocessing, document, registry/discovery, authoring, knowledge-base, and AI ownership rules continue to hold

#### Scenario: A later exception expands
- **WHEN** a new caller or target attempts to use a retained search or support/assembly exception
- **THEN** verification rejects the dependency outside its exact recorded pair

### Requirement: Search owns query and advanced-search implementations
Architecture verification SHALL require search to own query/ask and advanced-search API adaptation, workflows, deterministic policy, planning, retrieval, ranking, answering, durable run state, checkpoints, recovery, retention, and their persistence adapters. Foreign business features SHALL NOT depend on search implementation classes, repositories, or persistence records. Deterministic search rules SHALL NOT depend on persistence, provider clients, filesystem effects, or application assembly.

#### Scenario: A foreign workflow accesses search persistence
- **WHEN** architecture verification finds a business feature outside search depending on a search run repository or persistence record
- **THEN** verification rejects the dependency and identifies its origin

#### Scenario: Search policy acquires an external effect
- **WHEN** a deterministic search policy depends on a database adapter, provider client, or assembly implementation
- **THEN** verification rejects the dependency

### Requirement: Search consumes immutable foreign feature contracts
Search SHALL obtain document metadata, schema facts and parsing, and knowledge-base admission/association facts through public feature capabilities and consumer-owned ports. Boundary values SHALL be immutable and SHALL NOT expose foreign repositories, persistence records, binary paths, provider clients, or secrets. Synchronous reads SHALL retain caller transaction participation and established missing-resource behavior, schema snapshot semantics, and readiness/admission ordering.

#### Scenario: Readiness checks schema availability
- **WHEN** readiness inspects an absent active schema or checks an existing stored definition
- **THEN** it retains the existing informational text-only behavior or existence-based availability result without introducing schema parsing
- **AND** no external provider request is made

#### Scenario: A run is admitted
- **WHEN** capacity is reserved after initial readiness and run creation repeats readiness inside its relational transaction
- **THEN** public fact reads join that transaction and preserve profile identity/revision comparison and exact stored schema/settings capture
- **AND** existing rejection precedence and reservation release behavior remain unchanged

#### Scenario: A worker parses a captured schema
- **WHEN** a durable run contains captured schema identity, hash, and content
- **THEN** worker parsing consumes that captured content through a public schema contract rather than substituting the current active definition

#### Scenario: Search bypasses foreign ownership
- **WHEN** verification finds search reading a document, schema, or knowledge-base repository or implementation record directly
- **THEN** verification rejects the dependency

### Requirement: Search metadata boundaries preserve scoped batch bounds
Document metadata capabilities consumed by search SHALL provide scoped bounded batch citation lookup and bounded metadata-filter selection without exposing document internals. Search SHALL retain citation fallback/warning policy and retrieval deadline/diagnostic policy. Citation enrichment SHALL preserve its existing single batch lookup and identifier bound rather than introducing one lookup per evidence item.

#### Scenario: Citations reference repeated or unavailable documents
- **WHEN** evidence and expanded contexts contain duplicate, missing, or foreign document identifiers
- **THEN** one deduplicated bounded scoped lookup supplies available metadata
- **AND** missing or foreign sources retain existing fallback labels and warnings without exposing their metadata

#### Scenario: Metadata constraints select documents
- **WHEN** filename or content-type constraints are supplied for a knowledge base
- **THEN** selection remains relational, scoped, and bounded before owned chunk retrieval
- **AND** existing ordering, deadline outcomes, and channel diagnostics remain compatible

### Requirement: Search effect adapters retain graph and persistence ownership
Search SHALL own adapters for validated query execution, planner inspection, graph/text retrieval, parent-context reads, and advanced-search operational persistence. Shared infrastructure SHALL supply connections and transaction support. Search SHALL preserve row/deadline guardrails, scoped retrieval before limits, child text citations, and authoritative extraction-parent graph citations. Existing bounded index-readiness and legacy-labeling effects SHALL remain compatible without granting search document-processing or extracted-fact write ownership.

#### Scenario: A query reaches the database
- **WHEN** textual and schema validation succeeds
- **THEN** search-owned execution adapters use the configured connection/database and enforce the existing effective row and timeout policy

#### Scenario: Retrieval resolves evidence
- **WHEN** text, graph, or parent-context retrieval returns candidates
- **THEN** scope and provenance checks preserve existing child/parent citation semantics and branch-local failures

#### Scenario: Shared index maintenance is consumed
- **WHEN** document persistence or cleanup needs shared index support retained for final support migration
- **THEN** it does not acquire a dependency on search implementations
- **AND** existing index identities and legacy-corpus readiness behavior remain unchanged

### Requirement: Search compatibility and integration exceptions retire exactly
Search readiness and dense retrieval SHALL use AI-owned embedding compatibility and non-secret immutable target facts rather than the transitional compatibility bridge or duplicated rules. Search integration adapters SHALL only map consumer ports to provider public capabilities and immutable values; they SHALL NOT access repositories, own search policy, add transactions, or become production feature dependencies. Completion of step eight SHALL remove its exact document/schema exceptions and obsolete compatibility bridge, retaining only explicitly identified support/assembly seams assigned to step nine.

#### Scenario: Compatibility callers migrate
- **WHEN** readiness or dense retrieval inspects stored embeddings against the active target
- **THEN** AI-owned compatibility preserves existing blocker and branch outcomes
- **AND** verification rejects transitional bridge use or duplicated compatibility rules

#### Scenario: An integration adapter takes over policy
- **WHEN** a search integration adapter ranks evidence, chooses admission blockers, accesses persistence, or starts a transaction
- **THEN** verification rejects the misplaced responsibility

#### Scenario: A retired search exception remains
- **WHEN** verification scans migrated search components
- **THEN** no step-eight exception permits document metadata persistence or schema implementation access
- **AND** earlier feature boundary rules remain enforced

#### Scenario: A final support exception expands
- **WHEN** a new origin or target attempts to use a retained support/assembly exception
- **THEN** verification rejects the dependency outside the exact recorded pair and retirement step

### Requirement: Final feature ownership is enforced without roadmap exceptions

Architecture verification SHALL enforce owner-only access to document, schema, search, knowledge-base, AI and settings implementations. Cross-feature business dependencies SHALL use purpose-specific public capabilities and immutable facts within the declared feature graph; reverse runtime interactions SHALL use consumer-owned ports and assembly mappings. Completion of the modularization roadmap SHALL retire every exception assigned to step nine and SHALL replace each permitted permanent support or assembly dependency with an explicit role-based boundary rule. Earlier completed ownership restrictions SHALL remain enforced.

#### Scenario: A foreign feature reads implementation state
- **WHEN** verification finds a business feature accessing another owner's management workflow, mutable state record, repository or effect adapter
- **THEN** verification fails and identifies the originating dependency
- **AND** relocation or an exception formerly assigned to step nine does not authorize that access

#### Scenario: Roadmap completion is checked
- **WHEN** final architecture governance evaluates the completed migration
- **THEN** no roadmap step-nine exception remains
- **AND** permitted shared-support and assembly dependencies have explicit permanent rules with rejection coverage for forbidden neighboring dependencies

### Requirement: Settings supplies typed facts without owning feature interpretation

Settings SHALL own its catalog, codecs, validation, override persistence, lifecycle and typed configuration access. Consumers SHALL obtain immutable typed settings values without depending on settings persistence or management implementations. Settings SHALL NOT depend on business-feature implementations or feature-specific policy values. Feature-specific query-policy composition and chunker-revision derivation SHALL remain with their feature owners, with revision reporting obtained through a settings-owned port using one supplied immutable settings snapshot.

#### Scenario: Settings constructs a feature policy directly
- **WHEN** architecture verification finds settings depending on search policy types or document chunking implementations
- **THEN** verification rejects the dependency

#### Scenario: Effective chunk revision is reported
- **WHEN** settings reports the revision associated with an effective chunking configuration
- **THEN** document-owned revision derivation receives the same immutable settings snapshot used for that report through a mapped public capability
- **AND** existing hash inputs, revision values, alias precedence and response semantics are preserved without re-reading a different configuration or scheduling processing

#### Scenario: A deterministic rule consumes settings
- **WHEN** a deterministic feature rule needs configuration
- **THEN** it consumes immutable typed values
- **AND** verification rejects dependencies on live settings access or override persistence

### Requirement: AI capabilities separate public facts from secret and provider state

AI SHALL own profile management and persistence, model/provider construction, profile-scoped execution context, and deterministic embedding/tokenizer identity and compatibility policy. Foreign workflows SHALL consume immutable non-secret profile/target facts and execution capabilities rather than mutable profile records or management implementations. Provider SDK handles SHALL be confined to AI provider implementations and governed model adapters; public fact contracts and business workflows SHALL NOT expose provider clients or credentials. Knowledge bases SHALL retain lifecycle and profile/schema association ownership.

#### Scenario: A foreign workflow accesses a profile record
- **WHEN** verification finds a foreign workflow depending on mutable AI profile state, secret-bearing configuration or profile persistence
- **THEN** verification rejects that dependency

#### Scenario: A model adapter resolves a captured profile
- **WHEN** a governed model adapter invokes AI model resolution for a captured profile identity/revision
- **THEN** AI retains provider construction and context handling
- **AND** existing captured execution, nested restoration, model-selection fallback and cache behavior are preserved without exporting secret-bearing facts

#### Scenario: Knowledge-base admission uses AI facts
- **WHEN** knowledge-base provisioning or profile reassignment requires profile existence, defaults or embedding/tokenizer facts
- **THEN** it obtains those facts through public AI capabilities
- **AND** existing compatibility rejection, assignment ownership and transaction participation remain unchanged

### Requirement: Tokenizer identity has no document implementation dependency

Tokenizer identifiers and deterministic embedding-model tokenizer resolution SHALL be AI-owned immutable values/rules. AI and settings SHALL NOT depend on document chunking implementations to resolve tokenizer identity. Documents SHALL retain chunk splitting, counting mechanics and historical processing-snapshot restoration while consuming the AI-owned identity policy. Consolidation SHALL preserve persisted tokenizer identifiers, count modes, policy revisions, compatibility outcomes and chunker fingerprints.

#### Scenario: AI tokenizer resolution imports a chunker
- **WHEN** verification finds AI identity or compatibility policy depending on document tokenizer or chunking implementation types
- **THEN** verification rejects the dependency

#### Scenario: A historical processing snapshot is restored
- **WHEN** document processing restores an existing tokenizer/count-mode snapshot after consolidation
- **THEN** its supported identifier, count mode and revision retain the established interpretation
- **AND** relocation alone does not change compatibility or require re-embedding

### Requirement: Shared graph index support is independent of feature workflows

Shared graph index support SHALL provide narrowly governed identity, readiness, membership and scoped-retirement mechanics without depending on document or search workflow implementations. Documents SHALL retain chunk-write and cleanup authorization; search SHALL retain retrieval policy and branch/deadline interpretation; AI SHALL retain embedding-space compatibility rules. Index support SHALL preserve existing names, scope, dimensions, configured database, analyzer settings, legacy classification, readiness deadlines and idempotency.

#### Scenario: Document persistence prepares an index
- **WHEN** document persistence assigns chunk membership or creates a compatible scoped index
- **THEN** shared support performs the existing idempotent graph mechanics
- **AND** documents acquires no dependency on search implementations

#### Scenario: Retrieval checks index readiness
- **WHEN** search asks shared support to prepare or inspect a scoped vector or lexical index
- **THEN** existing scope, legacy-corpus behavior and deadline outcomes are preserved
- **AND** shared support does not read search operational state or take ownership of retrieval decisions

### Requirement: Generic observability and common support exclude feature interpretation

Generic observation, metadata-only logging, common immutable API values, binary storage and store-qualified transaction support SHALL expose explicitly governed public surfaces. Generic support SHALL NOT depend on business-feature workflows, persistence or feature-specific outcome/attempt types. Feature-specific metric interpretation SHALL remain feature-owned while preserving existing metric names/tags and observation behavior. Application HTTP assembly SHALL map owned API exceptions to existing problem contracts without accessing feature persistence or implementing feature policy.

#### Scenario: Generic observability imports search outcomes
- **WHEN** verification finds generic observation support depending on search answer, attempt or run implementation values
- **THEN** verification rejects that dependency
- **AND** feature-owned metric adaptation remains permitted under its owned adapter boundary

#### Scenario: Observation content capture is enabled
- **WHEN** a migrated feature emits model/workflow observations with content capture enabled
- **THEN** existing observation privacy controls, hierarchy and metric identities remain effective
- **AND** normal logs remain metadata-only

#### Scenario: A shared transaction annotation is consumed
- **WHEN** an owned application checkpoint or persistence adapter uses store-qualified transaction support
- **THEN** verification permits that support contract
- **AND** existing caller participation and relational-versus-external checkpoint separation remain unchanged

### Requirement: Assembly privileges do not extend to integration mappings

Application assembly SHALL own configuration factories, startup orchestration, persistence scanning and concrete implementation wiring. Business features and generic support SHALL NOT depend on assembly implementations. Bound configuration values consumed by features SHALL belong to their feature or support contracts. Integration mappings SHALL consume only consumer-owned ports, provider public capabilities and immutable values; they SHALL NOT access repositories, clients or filesystem effects, start transactions, or own feature policy. Assembly scanning/wiring privileges SHALL NOT authorize those mapping bypasses.

#### Scenario: Application wiring scans both stores
- **WHEN** application assembly initializes relational and graph persistence and owned executors
- **THEN** repository/entity coverage, transaction-manager routing, bean qualifiers, configured database and startup behavior remain compatible

#### Scenario: A feature imports assembly configuration
- **WHEN** verification finds a business workflow or generic support component importing a configuration factory or startup implementation
- **THEN** verification rejects that dependency
- **AND** owned immutable bound configuration values remain available through governed surfaces

#### Scenario: A mapping adapter uses assembly privileges
- **WHEN** an integration mapping accesses foreign persistence or external clients, adds a transaction, or implements feature policy
- **THEN** verification rejects the bypass despite its location under application assembly

### Requirement: Final ownership migration preserves historical and operational contracts

Support and assembly consolidation SHALL preserve HTTP paths, JSON/problem contracts, SQL mappings, stored historical snapshots, hashes/revisions, model/provider behavior, index/metric identities, configuration precedence, transaction participation and recovery semantics. Architecture verification SHALL retain precise independent transaction-governance constraints; unrelated historical transaction allowances SHALL NOT authorize foreign ownership violations or broad package exemptions.

#### Scenario: Existing persisted workflows are read after consolidation
- **WHEN** the application reads existing profiles, settings or document/schema/search workflow snapshots
- **THEN** existing fields, identifiers, revisions and historical interpretation remain compatible
- **AND** package relocation alone requires no SQL or binary migration

#### Scenario: External success precedes checkpoint failure
- **WHEN** a migrated workflow completes external work but its relational completion fails
- **THEN** its established recovery predicate and idempotent retry behavior remain available
- **AND** the migration introduces no cross-store transaction claim

#### Scenario: A historical transaction allowance is reviewed
- **WHEN** a pre-existing transactional self-call allowance remains after owner relocation
- **THEN** its exact scope and established transaction semantics remain explicitly governed
- **AND** it cannot permit foreign implementation access or expand to an owner-wide exemption
