## Why

The runtime settings catalog currently marks every `restart-required` entry as `mutable=false`, which makes the frontend treat startup-bound but externally manageable properties the same as genuinely read-only or profile-managed settings. Operators need to edit safe restart-required properties from the management UI, persist those changes through the backend, and understand that some values apply immediately while others apply only after the backend restarts.

## What Changes

- Redefine runtime setting mutability so `mutable=true` means "externally editable through the settings API", not "live-applied without restart".
- Allow selected `restart-required` settings to accept validated updates and clears through the runtime settings API while keeping `liveApplied=false` and `updateMode=restart-required`.
- Persist restart-required overrides in the existing runtime setting override storage so they are visible to clients before restart and can be used as startup configuration after restart.
- Keep truly read-only, sensitive read-only, profile-managed, application identity, pre-Neo4j-applied settings without runtime reassignment, Neo4j connectivity, and tightly coupled infrastructure settings non-mutable.
- Expose response metadata that lets the frontend distinguish live-applied overrides from pending restart overrides and active post-restart overrides.
- Make root logging level editable through the runtime settings API.
- Preserve AI profile management as the only edit path for provider base URL, API keys, chat model, embedding model, and embedding dimensions used by knowledge-base AI workflows.

## Capabilities

### New Capabilities

### Modified Capabilities
- `runtime-application-settings`: Change mutability semantics so allowlisted restart-required settings may be edited and persisted for application after restart, while live settings continue to apply immediately.
- `documentation-alignment`: Update runtime settings documentation to describe editable restart-required settings, pending restart behavior, and settings that remain non-editable.

## Impact

- Affected API: `/api/v1/runtime-settings` list, update, bulk update, and clear behavior; response semantics for `mutable`, `liveApplied`, `updateMode`, `source`, and restart-related metadata.
- Affected backend code: `RuntimeSettingsService`, runtime settings DTOs if additional pending/restart metadata is needed, override persistence, settings bootstrap/default resolution, validation tests, and controller tests.
- Affected frontend behavior: editable controls should be enabled for `mutable=true` settings regardless of whether `liveApplied` is true, and restart-required updates should be presented as pending restart.
- Affected docs: README and overlapping contributor guidance describing runtime settings mutability and restart behavior.
- No new external dependencies are expected.
