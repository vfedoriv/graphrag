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
- **THEN** repository documentation MUST describe which groups are live-editable, read-only, startup-bound, profile-managed, or sensitive
- **AND** documentation MUST direct AI provider behavior changes to AI profile management instead of raw application property updates
