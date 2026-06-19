## MODIFIED Requirements

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
