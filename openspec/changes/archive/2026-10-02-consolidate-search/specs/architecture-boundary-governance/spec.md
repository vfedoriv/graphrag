## ADDED Requirements

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
