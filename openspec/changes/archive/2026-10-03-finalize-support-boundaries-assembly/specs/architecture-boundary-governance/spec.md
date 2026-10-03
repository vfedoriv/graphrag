## ADDED Requirements

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
