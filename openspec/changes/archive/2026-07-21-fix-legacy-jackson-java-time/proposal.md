## Why

Starting a schema-draft evaluation fails when the draft has decision history because the shared legacy Jackson 2 mapper cannot serialize the `Instant` in each decision response. The failure prevents the required durable evaluation snapshot even though the request and selected held-out documents are valid.

## What Changes

- Configure the shared legacy Jackson 2 mapper with Java-time support and an explicit ISO-8601 string representation for date/time values.
- Ensure evaluation runs can snapshot non-empty decision histories before persistence and execution.
- Add focused serialization and evaluation regression coverage, including the persisted decision timestamp representation.
- Keep the HTTP API and Maven dependencies unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-evaluation`: Clarify that starting an evaluation with non-empty decision history durably snapshots every decision, including its timestamp, using the canonical JSON representation.

## Impact

- Affects the shared Jackson 2 configuration used by internal legacy `com.fasterxml.jackson` consumers.
- Affects schema-draft canonical JSON serialization and evaluation-run decision snapshots.
- Adds unit and integration regression tests around Java-time serialization and evaluation creation.
- Introduces no API contract break, database migration, frontend change, or new dependency.
