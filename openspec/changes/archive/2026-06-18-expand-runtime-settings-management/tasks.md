## 1. Settings Catalog Model

- [x] 1.1 Extend runtime setting definitions and response DTOs with update mode, restart/read-only reason, optional display metadata, and masked value support while preserving existing response fields.
- [x] 1.2 Add parser/storage/value-mapping support for any new value types needed by expanded settings, including string, duration/data-size display values, and masked sensitive values.
- [x] 1.3 Keep existing query, hybrid search, chunking, extraction, and AI observability definitions live-mutable with their current keys and persisted override behavior.

## 2. Expanded Property Coverage

- [x] 2.1 Add read-only catalog definitions for application identity, Spring AI bootstrap switches, Spring auto-configuration controls, Neo4j connection/database settings, storage root, multipart upload limits, actuator/health settings, tracing settings, and OpenTelemetry exporter settings.
- [x] 2.2 Add profile-managed or startup-bound catalog definitions for AI provider defaults from `app.model.*` and derived Spring AI OpenAI settings without duplicating AI profile update behavior.
- [x] 2.3 Mark API keys, Neo4j password, and OTLP authorization headers as sensitive and ensure list responses never include raw secret values.
- [x] 2.4 Decide and implement `logging.level.root` behavior as either live-mutable through Spring Boot `LoggingSystem` or read-only with an explicit reason.

## 3. Runtime Settings API Behavior

- [x] 3.1 Reject update requests for read-only, restart-required, profile-managed, and sensitive read-only settings with clear validation errors and no persisted override changes.
- [x] 3.2 Reject clear requests for settings that are allowlisted only for read-only visibility.
- [x] 3.3 Ensure list responses report profile-resolved startup defaults, override source for mutable settings, update mode, live-apply status, sensitivity, constraints, and read-only/restart reasons consistently.

## 4. Tests

- [x] 4.1 Update `RuntimeSettingsServiceTest` for expanded catalog coverage, update modes, read-only rejection, clear rejection, and preserved live accessors.
- [x] 4.2 Add assertions that sensitive runtime settings are masked and raw secrets are absent from current and default values.
- [x] 4.3 Add controller or integration coverage for list/update/clear API behavior for mutable, read-only, non-allowlisted, and sensitive settings.
- [x] 4.4 Add a catalog drift test or focused assertions that the relevant `application.properties` groups are represented with expected mutability and sensitivity.

## 5. Documentation

- [x] 5.1 Update README runtime settings and configuration sections with the expanded catalog, mutability categories, startup/profile-default behavior, secret masking, and AI-profile-managed provider changes.
- [x] 5.2 Keep overlapping implementation facts synchronized in `AGENTS.md` and `CLAUDE.md` where runtime settings or configuration management are documented.

## 6. Verification

- [x] 6.1 Run focused runtime settings tests.
- [x] 6.2 Run `./mvnw test`.
- [x] 6.3 Run `graphify update .` after implementation changes.
