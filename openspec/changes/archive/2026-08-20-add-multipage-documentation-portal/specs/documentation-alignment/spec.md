## ADDED Requirements

### Requirement: Repository provides a canonical multipage documentation portal
The repository SHALL provide a backend-owned documentation portal whose Markdown sources are readable in the source repository and organized for users, operators, integrators, and contributors.

#### Scenario: Reader enters through repository documentation
- **WHEN** a reader opens `README.md` or the portal index
- **THEN** the reader can navigate to getting-started, concept, workflow, operations, reference, and contributor documentation
- **AND** `README.md` identifies the portal as the canonical detailed documentation

#### Scenario: Reader follows a major product workflow
- **WHEN** a reader selects knowledge-base/profile setup, schema lifecycle, schema drafts, document processing, chunking/reprocessing, Cypher queries, or advanced search
- **THEN** the portal explains the workflow ordering, ownership boundaries, relevant state transitions, representative examples, failure behavior, and related references

### Requirement: Documentation is generated through the Maven toolchain
The repository SHALL generate navigable HTML documentation through the Maven Wrapper without requiring Node, React, Vite, or Python.

#### Scenario: Contributor builds the portal
- **WHEN** a contributor runs `./mvnw site`
- **THEN** Maven Site generates the complete portal under `target/site`
- **AND** every configured navigation target and required static resource is present

#### Scenario: Contributor previews the portal
- **WHEN** a contributor runs `./mvnw site:run`
- **THEN** the generated documentation is available through the Maven Site local preview server

### Requirement: Documentation diagrams render from portable sources
The repository SHALL author architecture, lifecycle, state, and data-flow diagrams as standard Mermaid fences and SHALL render them in both GitHub Markdown and generated Maven Site pages.

#### Scenario: Generated page contains a Mermaid diagram
- **WHEN** Maven Site renders a Markdown page containing a `mermaid` fence
- **THEN** the generated page loads the pinned browser renderer and initializes that diagram
- **AND** documentation generation does not require a Node or Python toolchain

### Requirement: Documentation navigation and local references are verified
The repository SHALL deterministically validate portal navigation, same-repository links, image targets, selected implementation-backed facts, and required cross-repository references.

#### Scenario: Documentation reference becomes invalid
- **WHEN** a portal page is absent from navigation or a relative document or image target does not exist
- **THEN** the documentation alignment check fails with the source document and invalid target

#### Scenario: Documentation site build is checked
- **WHEN** documentation-related files change in continuous integration
- **THEN** the focused documentation alignment test and Maven Site build run without requiring persistence containers or AI credentials

### Requirement: Backend and frontend documentation are reciprocally linked
The backend portal SHALL identify itself as the canonical cross-stack documentation and SHALL link to the frontend repository's operator-focused guides; frontend documentation SHALL provide stable links back to the canonical backend pages through its coordinated repo-local change.

#### Scenario: Reader crosses repository boundaries
- **WHEN** a reader follows a backend link to frontend controls or a frontend link to canonical system behavior
- **THEN** the target uses a stable repository URL rather than a machine-local filesystem path
- **AND** the originating page describes which repository owns the referenced documentation

## MODIFIED Requirements

### Requirement: Repository guidance reflects implemented API surface
The repository SHALL document the currently implemented public API endpoints that are intended for contributor and consumer use, including schema, schema-draft, document, knowledge-base, runtime-setting, AI-profile, query, chunking/reprocessing, and advanced-search workflows.

#### Scenario: Public API workflow is documented
- **WHEN** the implementation exposes or materially changes a public workflow
- **THEN** the canonical portal MUST describe the workflow and its representative request shapes consistently with the controllers
- **AND** `README.md` MUST link readers to the canonical workflow documentation
- **AND** the portal MUST direct readers to runtime Swagger/OpenAPI for exhaustive endpoint and DTO schemas

### Requirement: Repository guidance reflects implemented configuration defaults
The repository SHALL document implementation-backed configuration defaults when those defaults materially affect local usage, processing behavior, provider setup, runtime settings, externally manageable settings, or AI profile seeding.

#### Scenario: Extraction limits change in application config
- **WHEN** extraction limits or provider defaults are updated in application properties
- **THEN** the canonical portal, `README.md`, `AGENTS.md`, and `CLAUDE.md` MUST not retain conflicting values or stale descriptions for those documented settings

#### Scenario: Runtime settings and AI profiles are added
- **WHEN** the implementation exposes runtime settings or AI provider profile management
- **THEN** the canonical portal, `AGENTS.md`, and `CLAUDE.md` MUST describe the source of defaults, persistence behavior, live-apply behavior, secret masking behavior, and restart or compatibility limitations consistently where those facts are documented

#### Scenario: Externally manageable settings catalog changes
- **WHEN** the runtime settings API exposes additional application property groups for frontend management
- **THEN** repository documentation MUST describe which groups are live-editable, restart-required editable, read-only, profile-managed, or sensitive
- **AND** documentation MUST explain that `mutable=true` means editable through the settings API, while `liveApplied` and `updateMode` describe whether the value applies immediately or after restart
- **AND** documentation MUST explain the pending state for restart-required overrides and when it becomes active after backend restart
- **AND** documentation MUST state that settings consumed before PostgreSQL-backed overrides can load remain deployment-managed unless the implementation provides a safe runtime reassignment path
- **AND** documentation MUST state that PostgreSQL and Neo4j connectivity remain deployment-managed through environment variables, Docker Compose, or equivalent deployment configuration rather than the runtime settings UI
- **AND** documentation MUST identify root logging level as editable and describe whether it applies live or after restart according to the implementation
- **AND** documentation MUST direct AI provider behavior changes to AI profile management instead of raw application property updates
