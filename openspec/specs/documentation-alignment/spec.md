# documentation-alignment Specification

## Purpose
Keep repository user and contributor documentation aligned with implemented public behavior and configuration defaults.
## Requirements
### Requirement: Repository guidance reflects implemented API surface
The repository SHALL document the currently implemented public API endpoints that are intended for contributor and consumer use, including schema, document, knowledge-base, and query workflows.

#### Scenario: Schema endpoints are documented completely
- **WHEN** the implementation exposes schema creation, generation, example generation, validation, listing, lookup, knowledge-base listing, and activation endpoints
- **THEN** `README.md` MUST describe those endpoints and their supported request shapes consistently with the controllers

### Requirement: Repository guidance reflects implemented configuration defaults
The repository SHALL document implementation-backed configuration defaults when those defaults materially affect local usage, processing behavior, provider setup, runtime settings, externally manageable settings, or AI profile seeding.

#### Scenario: Extraction limits change in application config
- **WHEN** extraction limits or provider defaults are updated in application properties
- **THEN** `README.md`, `AGENTS.md`, and `CLAUDE.md` MUST not retain conflicting values or stale descriptions for those documented settings

#### Scenario: Runtime settings and AI profiles are added
- **WHEN** the implementation exposes runtime settings or AI provider profile management
- **THEN** `README.md`, `AGENTS.md`, and `CLAUDE.md` MUST describe the source of defaults, persistence behavior, live-apply behavior, secret masking behavior, and restart or compatibility limitations consistently where those facts are documented

#### Scenario: Externally manageable settings catalog changes
- **WHEN** the runtime settings API exposes additional application property groups for frontend management
- **THEN** repository documentation MUST describe which groups are live-editable, restart-required editable, read-only, profile-managed, or sensitive
- **AND** documentation MUST explain that `mutable=true` means editable through the settings API, while `liveApplied` and `updateMode` describe whether the value applies immediately or after restart
- **AND** documentation MUST explain the pending state for restart-required overrides and when it becomes active after backend restart
- **AND** documentation MUST state that settings consumed before Neo4j-backed overrides can load remain deployment-managed unless the implementation provides a safe runtime reassignment path
- **AND** documentation MUST state that Neo4j connectivity remains deployment-managed through environment variables, Docker Compose, or equivalent deployment configuration rather than the runtime settings UI
- **AND** documentation MUST identify root logging level as editable and describe whether it applies live or after restart according to the implementation
- **AND** documentation MUST direct AI provider behavior changes to AI profile management instead of raw application property updates

### Requirement: Contributor guidance stays synchronized across repo instruction files
The repository SHALL keep overlapping contributor guidance synchronized across `README.md`, `AGENTS.md`, and `CLAUDE.md` when the same implementation fact is presented in more than one file.

#### Scenario: Shared guidance changes
- **WHEN** a controller capability or contributor workflow is added, removed, or materially revised
- **THEN** all affected instruction files MUST be updated in the same change so they do not present conflicting guidance

### Requirement: Documented stack versions are build-backed
The repository SHALL keep documented Java, Spring Boot, Spring AI, and LangChain4j version facts aligned with the Maven build configuration wherever those facts are stated.

#### Scenario: Maven version property changes
- **WHEN** a documented stack version changes in `pom.xml`
- **THEN** README, AGENTS, and CLAUDE do not retain conflicting version claims or stale version-specific links

#### Scenario: Documentation alignment check runs
- **WHEN** the documentation alignment regression check runs
- **THEN** it identifies conflicting shared stack/configuration facts before merge

### Requirement: Contributor and operator guidance reflects the final persistence topology
The system documentation SHALL consistently describe PostgreSQL as required operational storage, Neo4j as graph-only storage, shared-server isolation, supported profiles, provisioning, startup, and the current reset-only fresh-start commands.

#### Scenario: Shared guidance is updated
- **WHEN** persistence startup or reset documentation is changed
- **THEN** overlapping facts in `README.md`, `AGENTS.md`, and `CLAUDE.md` are synchronized in the same change
- **AND** commands use the Maven Wrapper and the implemented provisioning path

#### Scenario: Disposable GraphRAG state is reset
- **WHEN** the one-time routing correction is documented
- **THEN** guidance states that existing GraphRAG PostgreSQL and Neo4j data is intentionally discarded
- **AND** does not require backup, restore, quarantine, or migration procedures
- **AND** cleanup targets GraphRAG-owned state without deleting Langfuse-owned tables

### Requirement: Persistence guidance describes effective datasource precedence
Repository documentation SHALL explain that the `langfuse-postgres` Compose service is ignored for Spring Boot service-connection discovery and that local GraphRAG startup uses its explicit `GRAPHRAG_POSTGRES_*` datasource configuration.

#### Scenario: Local startup guidance is followed
- **WHEN** an operator provisions the empty GraphRAG database and starts the application
- **THEN** the documented commands route GraphRAG to `graphrag / graphrag / app`
- **AND** do not present Langfuse's bootstrap database as a GraphRAG datasource

### Requirement: Advanced-search examples match the public request contract
Repository guidance and generated OpenAPI examples SHALL use the implemented `maximumEvidence` request property and SHALL describe the asynchronous advanced-search lifecycle without advertising the retired Hybrid Search endpoint.

#### Scenario: Documented submission example is serialized
- **WHEN** the advanced-search request example is exercised by a controller contract test
- **THEN** its evidence bound is read from `maximumEvidence` rather than silently falling back to a default

#### Scenario: Endpoint migration guidance is checked
- **WHEN** public API migration documentation is validated
- **THEN** it directs clients to advanced-search run creation, polling, and result resources and does not restore or advertise a Hybrid Search compatibility route
