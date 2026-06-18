## Why

Updating runtime settings one key at a time forces clients to make multiple API calls for related configuration changes, which adds unnecessary request overhead and makes coordinated settings updates harder to reason about.

## What Changes

- Add a bulk runtime settings update endpoint under `/api/v1/runtime-settings`.
- Accept multiple setting updates in one request while reusing the existing allowlist, mutability, type, and constraint validation rules.
- Apply bulk updates atomically: if any submitted setting is invalid or not mutable, no setting override is changed.
- Return the updated runtime setting representations for the settings included in the request.
- Keep the existing single-setting update and clear endpoints unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `runtime-application-settings`: Add a bulk update contract for changing multiple mutable runtime settings in one request.

## Impact

- Affected API: `PUT /api/v1/runtime-settings/{key}` remains unchanged; a new collection-level bulk update endpoint is added.
- Affected code: runtime settings controller, DTOs, service update flow, and controller/service tests.
- Affected systems: persisted runtime setting overrides in Neo4j, with no new external dependency.
