## ADDED Requirements

### Requirement: Repository guidance reflects implemented API surface
The repository SHALL document the currently implemented public API endpoints that are intended for contributor and consumer use, including schema, document, knowledge-base, and query workflows.

#### Scenario: Schema endpoints are documented completely
- **WHEN** the implementation exposes schema creation, generation, example generation, validation, listing, lookup, knowledge-base listing, and activation endpoints
- **THEN** `README.md` MUST describe those endpoints and their supported request shapes consistently with the controllers

### Requirement: Repository guidance reflects implemented configuration defaults
The repository SHALL document implementation-backed configuration defaults when those defaults materially affect local usage, processing behavior, or provider setup.

#### Scenario: Extraction limits change in application config
- **WHEN** extraction limits or provider defaults are updated in application properties
- **THEN** `README.md`, `AGENTS.md`, and `CLAUDE.md` MUST not retain conflicting values or stale descriptions for those documented settings

### Requirement: Contributor guidance stays synchronized across repo instruction files
The repository SHALL keep overlapping contributor guidance synchronized across `README.md`, `AGENTS.md`, and `CLAUDE.md` when the same implementation fact is presented in more than one file.

#### Scenario: Shared guidance changes
- **WHEN** a controller capability or contributor workflow is added, removed, or materially revised
- **THEN** all affected instruction files MUST be updated in the same change so they do not present conflicting guidance
