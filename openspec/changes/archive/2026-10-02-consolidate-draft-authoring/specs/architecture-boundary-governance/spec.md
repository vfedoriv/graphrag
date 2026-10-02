## ADDED Requirements

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
