## Why

Reanalyzing a schema draft creates new unresolved conflict records while the conflict list also returns resolved records from older aggregate revisions. This makes equivalent conflicts appear twice with contradictory statuses and forces users to repeat resolutions even when the conflict alternatives have not changed.

## What Changes

- Scope the default draft-conflict list to the draft's current aggregate revision so the active review surface cannot mix current and historical conflicts.
- Keep conflicts from prior aggregate revisions durable and expose them only through an explicit history scope.
- Reapply a prior explicit resolution when reanalysis produces a semantically equivalent conflict with the same type, coordinate, and canonical alternatives.
- Leave a reanalyzed conflict unresolved when its type, coordinate, or alternatives differ, even if an older conflict used the same display label.
- Expose aggregate lineage and currentness in conflict responses so clients can render current and historical records unambiguously.
- **BREAKING**: the default conflict-list response changes from draft-wide records to current-aggregate records; clients using that endpoint as implicit history must request the explicit history scope.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-review`: Define aggregate-scoped conflict retrieval, explicit conflict history, and safe resolution carry-forward across reanalysis.

## Impact

- Affects schema-draft conflict persistence and reconciliation in `SchemaDraftAnalysisService` and `SchemaDraftReviewService`.
- Affects conflict repository queries, conflict DTOs, controller/OpenAPI contract, and the Conflicts review UI.
- Requires deterministic semantic conflict matching and tests covering equivalent, changed, current, stale, and historical aggregate revisions.
- Does not change candidate decision reconciliation or publication readiness semantics; those already operate against the current aggregate.
