## ADDED Requirements

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
