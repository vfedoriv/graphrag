## 1. Conflict Identity and Persistence

- [x] 1.1 Add a deterministic semantic conflict key based on type, coordinate, and the sorted canonical alternative set, with unit coverage for reordered and changed alternatives.
- [x] 1.2 Add aggregate-scoped and deterministic draft-history conflict repository queries needed by reconciliation and retrieval.
- [x] 1.3 Add conflict response lineage fields for aggregate revision identifier and derived currentness.

## 2. Resolution Reconciliation

- [x] 2.1 Reconcile newly persisted aggregate conflicts against the latest resolved historical conflict with the same semantic key.
- [x] 2.2 Carry selected-alternative resolutions only when the selected value remains in the current normalized alternatives.
- [x] 2.3 Carry custom resolutions only after applying current resolution validation, leaving invalid or incomplete historical resolutions unresolved.
- [x] 2.4 Preserve new conflict records and historical records independently without mutating or deleting prior aggregate lineage.

## 3. Current and Historical Conflict API

- [x] 3.1 Add a typed `CURRENT`/`ALL` conflict-list scope with `CURRENT` as the default controller contract.
- [x] 3.2 Return only current-aggregate conflicts for the default scope, return an empty collection when no current aggregate exists, and exclude non-promoted aggregate conflicts.
- [x] 3.3 Return all draft conflicts for explicit history scope with stable aggregate-revision, coordinate, and identifier ordering.
- [x] 3.4 Update OpenAPI descriptions and examples to document the default behavior change, history scope, lineage fields, and currentness semantics.

## 4. Verification

- [x] 4.1 Add service tests proving equivalent conflicts retain selected and valid custom resolutions across reanalysis.
- [x] 4.2 Add service tests proving changed alternatives, changed conflict types, and invalid custom resolutions remain unresolved.
- [x] 4.3 Add controller/integration tests proving current listing contains no historical duplicates and explicit history returns both current and prior records with correct lineage.
- [x] 4.4 Add regression coverage proving stale or non-promoted analysis conflicts remain historical and do not affect current projection or publication readiness.
- [x] 4.5 Run focused schema-draft tests, the full Maven test suite, OpenSpec validation, and `graphify update .`.
