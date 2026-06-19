## Why

Runtime setting overrides now use Neo4j optimistic locking so assigned setting-key identifiers are handled as existing entities on repeat saves. Existing deployments may already contain `RuntimeSettingOverride` nodes without the new version property, which can cause `/api/v1/runtime-settings` reads to fail when lifecycle reconciliation saves those records.

## What Changes

- Treat version metadata as part of the runtime setting override persistence contract.
- Backfill missing runtime setting override versions before loading or reconciling persisted overrides.
- Ensure existing persisted overrides continue to list, update, clear, and reconcile lifecycle state after the versioned entity change.
- Cover the compatibility path with runtime settings service tests.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `runtime-application-settings`: Clarify that existing persisted override records without version metadata must be migrated or normalized before versioned persistence operations.

## Impact

- Affected API: `/api/v1/runtime-settings` list, update, and clear behavior remains compatible with existing persisted overrides.
- Affected backend code: `RuntimeSettingOverrideRepository`, `RuntimeSettingsService`, and runtime settings service tests.
- Affected data: existing Neo4j `RuntimeSettingOverride` nodes may receive a missing `version` property during runtime settings load.
