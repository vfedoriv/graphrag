## Why

The runtime settings API currently covers only a subset of backend-owned tuning knobs, while `application.properties` contains additional operational settings that a frontend management app should be able to inspect and, where safe, update. Expanding the settings catalog now gives operators a single external management surface without making arbitrary Spring Boot configuration mutable at runtime.

## What Changes

- Expand the runtime settings catalog to include all relevant `application.properties` groups with explicit metadata for mutability, live-apply behavior, sensitivity, source, and constraints.
- Keep existing live-editable query, hybrid search, chunking, extraction, and AI observability privacy/tag settings mutable through persisted overrides.
- Add externally visible read-only entries for startup-bound infrastructure settings such as Spring application identity, Neo4j connection/database settings, document storage root, multipart upload limits, Spring AI bootstrap model switches, actuator/tracing exporter settings, and Spring auto-configuration controls.
- Treat secrets as sensitive: read responses must not expose actual API keys, Neo4j passwords, or OTLP authorization headers.
- Allow startup-bound settings to be represented but not updated through the runtime override endpoint unless implementation can safely apply them to subsequent work without restart.
- Preserve AI provider profile management as the way to update provider base URL, API key, chat model, embedding model, and embedding dimensions for knowledge-base AI workflows.

## Capabilities

### New Capabilities

### Modified Capabilities
- `runtime-application-settings`: Broaden the settings inventory and clarify read-only, sensitive, startup-bound, and live-editable behavior for external management clients.
- `documentation-alignment`: Document the expanded settings catalog, mutability rules, restart limitations, and secret masking behavior consistently.

## Impact

- Affected API: `/api/v1/runtime-settings` list, update, and clear behavior and its response metadata.
- Affected backend code: `RuntimeSettingsService`, runtime settings DTOs, typed runtime setting accessors, validation tests, controller tests, and settings persistence behavior.
- Affected configuration: `src/main/resources/application.properties` and profile-specific overrides remain the source of startup defaults.
- Affected docs: README and overlapping contributor guidance describing runtime settings, configuration defaults, and externally manageable settings.
- No new external dependencies are expected.
