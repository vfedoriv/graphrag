## Context

Runtime setting overrides are persisted as Neo4j nodes keyed by setting name. The node uses an assigned identifier, so Spring Data Neo4j needs stable entity state information to distinguish repeat saves from inserts. Adding optimistic locking metadata solves the repeat-save warning for newly saved records, but existing deployments can already have override nodes that were created before the version property existed.

The runtime settings list path may reconcile lifecycle state while building responses. If an older override is loaded without version metadata and then saved during reconciliation, Spring Data Neo4j treats the save as a versioned update for a required version that does not exist, causing `/api/v1/runtime-settings` to fail.

## Goals / Non-Goals

**Goals:**

- Preserve existing runtime setting override data after introducing versioned persistence.
- Ensure runtime settings list, update, clear, and lifecycle reconciliation can operate on legacy override nodes.
- Keep the compatibility path narrow and local to runtime setting override persistence.
- Cover the compatibility behavior with a service-level regression test.

**Non-Goals:**

- Introduce a general database migration framework.
- Change the runtime settings API response shape.
- Change runtime setting keys, stored values, or validation semantics.
- Address assigned-id warnings for unrelated entities.

## Decisions

1. Backfill missing `RuntimeSettingOverride.version` values in Neo4j before loading persisted restart-required overrides.

   Rationale: this normalizes legacy data before the service performs reads that may later be followed by lifecycle reconciliation saves. It keeps the migration self-contained and idempotent.

   Alternative considered: catch `OptimisticLockingFailureException` during reconciliation and retry with a fresh entity. That would make the hot path more complex and would still leave old data malformed for future operations.

2. Use a targeted repository query rather than application-side scan and save.

   Rationale: `MATCH (override:RuntimeSettingOverride) WHERE override.version IS NULL SET override.version = 0` updates only affected records without loading them into versioned entity state first.

   Alternative considered: load all overrides and call repository `save`. That is the operation that fails for legacy rows, so it is unsuitable as a repair mechanism.

3. Keep backfill idempotent and safe to run whenever persisted restart-required overrides are loaded.

   Rationale: the query only touches records missing version metadata. Re-running it after deployment or during tests has no effect on already-versioned records.

## Risks / Trade-offs

- [Risk] The backfill runs on every service initialization or first persisted override load. -> Mitigation: the query is constrained to `RuntimeSettingOverride` nodes with missing `version`, so the steady-state cost is small.
- [Risk] Existing records with a non-null but invalid version value are not repaired. -> Mitigation: the observed compatibility failure is missing version metadata from pre-version records; broader data repair remains outside this focused change.
- [Risk] Similar assigned-id warnings can exist for other Neo4j entities. -> Mitigation: this change is scoped to the runtime settings failure path; unrelated entity persistence should be handled by separate specs if needed.

## Migration Plan

Deploy the runtime settings compatibility change with the application. On startup or first runtime settings access, the service backfills missing version metadata on existing `RuntimeSettingOverride` nodes before reading persisted overrides. Rollback is safe because the added `version` property can remain on nodes even if older code ignores it.

## Open Questions

None.
