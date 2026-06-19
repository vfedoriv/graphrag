## Why

Updating runtime application settings currently logs a Spring Data Neo4j warning that `RuntimeSettingOverrideNode` instances with an assigned id are always treated as new because the entity has no version property. This is noisy during normal operator workflows and signals that override persistence is not using Spring Data Neo4j's recommended state detection for business-key ids.

## What Changes

- Add proper persistence state tracking for runtime setting override nodes that use the setting key as an assigned id.
- Update runtime setting override save paths to preserve existing persisted override state when updating values or lifecycle metadata.
- Keep the runtime settings API contract unchanged: valid updates, clears, list responses, lifecycle metadata, and live application behavior continue to work as before.
- Add focused regression coverage proving repeated updates of the same runtime setting do not rely on always-new entity handling.

## Capabilities

### New Capabilities

### Modified Capabilities
- `runtime-application-settings`: Runtime setting override persistence must use stable entity state handling for assigned-id override records.

## Impact

- Affected backend code: `RuntimeSettingOverrideNode`, `RuntimeSettingsService`, and runtime settings persistence tests.
- Affected storage behavior: existing `RuntimeSettingOverride` records should continue to load and update in place; newly saved records should include version metadata managed by Spring Data Neo4j.
- Affected API: no request or response shape changes are intended.
- Dependencies: no new external dependencies.
