## Context

Runtime setting overrides are stored as `RuntimeSettingOverride` Neo4j nodes keyed by the setting key. The key is an assigned business id rather than a generated database id. Spring Data Neo4j warns when saving such entities without a `@Version` property because it cannot distinguish new assigned-id entities from existing persisted entities.

The current runtime settings update path creates a fresh `RuntimeSettingOverrideNode`, assigns the key, and saves it for both single and bulk updates. That preserves API behavior, but it causes normal updates to log `DefaultNeo4jIsNewStrategy` warnings and leaves existing override state handling dependent on SDN's fallback behavior.

## Goals / Non-Goals

**Goals:**

- Remove the assigned-id warning during normal runtime setting updates.
- Map `RuntimeSettingOverrideNode` in the Spring Data Neo4j-supported pattern for assigned ids.
- Preserve existing persisted override records, lifecycle state reconciliation, and API response semantics.
- Keep single and bulk updates atomic and validation-first.
- Add regression coverage for repeated updates of an existing override.

**Non-Goals:**

- Changing runtime settings request or response DTO shapes.
- Replacing the setting key as the override identity.
- Adding new uniqueness constraints or database migrations beyond the mapped version property.
- Refactoring unrelated runtime settings catalog behavior.

## Decisions

1. Add a Spring Data `@Version` field to `RuntimeSettingOverrideNode`.

   Rationale: Spring Data Neo4j documents `@Version` as the state detection mechanism for entities with assigned business ids. This matches the existing pattern used by `DocumentUploadNode` and avoids introducing custom `Persistable` logic.

   Alternative considered: implement `Persistable<String>` and custom `isNew()` state. Rejected because it requires manual state management after loads and saves, and `@Version` is the framework-supported path for this case.

2. Update existing override nodes in place when possible.

   Rationale: Loading `repository.findById(key).orElseGet(...)`, mutating the loaded node, and saving it preserves version state for existing records and makes repeated updates explicit. For new records, the node starts with no version and SDN creates it normally.

   Alternative considered: keep constructing fresh nodes after adding `@Version`. Rejected because fresh nodes do not carry the persisted version on updates and can still exercise new-entity behavior unnecessarily.

3. Keep lifecycle reconciliation as a normal save of a loaded entity.

   Rationale: `toResponse` already loads the override before reconciling stale lifecycle state. Once the entity has a version property, saving that loaded object should update in place without warning.

## Risks / Trade-offs

- [Risk] Existing `RuntimeSettingOverride` nodes do not have a version property. -> Mitigation: Spring Data Neo4j can load a null version as existing state when the entity is read; the next save writes managed version metadata.
- [Risk] Tests backed only by mocks may miss mapping warnings. -> Mitigation: keep unit tests for update behavior and add or adjust integration-level coverage if warning regression cannot be asserted through unit tests.
- [Risk] Bulk update behavior could become partially applied if update nodes are loaded and saved before all validation completes. -> Mitigation: keep the current parse/validate-all-first structure, then resolve and save entities only after every submitted update is valid.

## Migration Plan

1. Add `@Version Long version` and a getter to `RuntimeSettingOverrideNode`.
2. Introduce a helper in `RuntimeSettingsService` for loading or creating an override node by key.
3. Reuse that helper in single and bulk update persistence paths.
4. Update tests for repeated updates and version-aware existing nodes.
5. Run focused runtime settings tests and the full Maven test suite.

Rollback is to remove the version field and return to fresh-node saves, accepting the warning again.

## Open Questions

- None.
