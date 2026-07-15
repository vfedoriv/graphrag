## Why

The schema-draft backend persists enough state for the planned frontend workflow, but several public contracts are opaque or incomplete in generated API metadata. A frontend cannot safely restore guidance, render candidates and evaluation results without guessing JSON shapes, or paginate run outcomes consistently.

## What Changes

- Replace arbitrary draft guidance request values with a validated typed guidance contract that preserves both free-form instructions and structured discovery guidance.
- Return the current guidance value together with its revision and fingerprint when a draft is read, so clients can reopen and update it without destructive defaults.
- Replace the ad hoc candidate page map with an explicit typed page response whose candidate items expose discovery data and effective persistent review state.
- Replace generic evaluation metrics and advisory assessment values with explicit metric, applicability, evidence, advisory-status, reason, and reproducibility contracts.
- **BREAKING**: Standardize candidate, analysis outcome, evaluation outcome, and reprocessing item pagination around `page`, `size`, `totalElements`, and `content`, replacing the existing list-plus-count fields embedded in status responses.
- **BREAKING**: Reject guidance payloads that do not conform to the new typed guidance contract; normalize already persisted legacy guidance into the typed response shape.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-lifecycle`: Draft create, update, and read operations use a readable, validated guidance contract.
- `schema-draft-review`: Candidate retrieval returns a typed page and effective persistent review state.
- `schema-draft-analysis`: Per-source outcomes use the standard page envelope.
- `schema-draft-evaluation`: Metrics, advisory assessments, evidence, and per-document pagination become explicit API contracts.
- `schema-reprocessing-plans`: Per-document plan outcomes use the standard page envelope.

## Impact

- Affects `SchemaDraftController`, `SchemaReprocessingPlanController`, draft/evaluation/reprocessing DTOs, lifecycle/review/evaluation services, OpenAPI output, and controller/integration tests.
- Reuses the structured discovery guidance and candidate contracts while adding draft-specific response state.
- Requires compatibility reads for guidance JSON already persisted by the current draft implementation.
- Requires frontend clients to adopt the standardized nested page envelopes before consuming the changed status responses.
