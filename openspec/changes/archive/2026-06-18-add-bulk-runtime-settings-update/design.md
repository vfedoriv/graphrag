## Context

Runtime settings are exposed at `/api/v1/runtime-settings`. Clients can list all allowlisted settings, update one setting with `PUT /api/v1/runtime-settings/{key}`, and clear one setting with `DELETE /api/v1/runtime-settings/{key}`. Each update goes through the runtime settings allowlist, mutability checks, value parsing, validation, and Neo4j persistence.

Clients that need to change related settings currently issue one request per setting. This creates unnecessary overhead and can leave clients with partial changes when one request succeeds and a later request fails.

## Goals / Non-Goals

**Goals:**

- Add a collection-level bulk update endpoint for multiple runtime setting values.
- Reuse existing validation, mutability, parsing, and response semantics.
- Make bulk updates all-or-nothing so invalid input cannot partially change persisted overrides.
- Preserve existing single-setting update and clear behavior.

**Non-Goals:**

- Bulk clear/delete of runtime setting overrides.
- Adding new mutable settings or changing the runtime settings allowlist.
- Runtime reconfiguration for startup-bound, profile-managed, read-only, or sensitive read-only settings.
- Changing the list response shape or existing single-setting update request shape.

## Decisions

1. Use `PUT /api/v1/runtime-settings` for bulk update.

   The endpoint operates on the runtime settings collection and keeps the existing key-scoped endpoint unchanged. `PATCH` was considered, but the existing single-setting write API already uses `PUT`, and the operation replaces the submitted settings' override values rather than applying a partial document patch.

2. Use an explicit updates array request body.

   The request body should be shaped as `{"updates":[{"key":"app.query.max-rows","value":25}]}`. An array preserves client order for the response and leaves room for per-entry validation metadata later. A raw object map was considered, but it cannot represent duplicate keys cleanly and has less room to evolve.

3. Reject empty requests and duplicate keys.

   Empty bulk updates are most likely client mistakes and do not justify a successful no-op. Duplicate keys make response ordering and "last write wins" behavior ambiguous, so the API rejects them before persistence.

4. Validate all entries before persisting any entry.

   The service should first resolve each definition, check mutability, parse values, and validate constraints for every submitted update. Only after all entries pass validation should it persist overrides in a transaction. This preserves the current validation behavior while preventing partial bulk updates.

5. Return updated setting responses in request order.

   Returning the same `RuntimeSettingResponse` shape keeps the API consistent with the list and single-setting update endpoints. Request order is useful for clients correlating responses without requiring a separate lookup.

## Risks / Trade-offs

- Partial persistence from implementation mistakes -> Keep validation and persistence as separate phases inside a transactional service method, and cover failure cases with service tests.
- Concurrent updates to overlapping keys -> Existing single-key last-write behavior still applies at commit time; this change does not introduce optimistic locking.
- Larger request bodies -> The endpoint is limited to allowlisted runtime setting keys and normal application request limits; no separate payload size control is needed for this change.
