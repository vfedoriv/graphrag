## ADDED Requirements

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
