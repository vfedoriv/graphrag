## Why

Schema-draft analysis detail and history responses use the same `retryable` field for two different concepts: persisted failure retryability and current retry-command eligibility. This allows one run to report contradictory values and makes history unsafe as the source for a retry action without understanding endpoint-specific semantics.

## What Changes

- Define analysis-run `retryable` consistently as the persisted classification of whether the run contains a retryable failure.
- Add an explicit `canRetry` field to analysis detail and history summaries for current retry-command eligibility.
- Preserve retrying completed and permanently failed runs when current resource state permits it; persisted failure retryability does not become an endpoint gate.
- Centralize deterministic retry eligibility so history/detail projections and retry-command validation use the same resource-state rules.
- Document that `canRetry` can change as the draft, source membership, or concurrent analysis state changes and does not guarantee acceptance across a race or transient queue-capacity failure.
- **BREAKING**: Change analysis-history `retryable` from command eligibility to persisted failure retryability. Existing consumers using history to render retry actions must migrate to `canRetry`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-analysis`: Separate persisted failure retryability from retry-command eligibility and align history, detail, and command validation semantics.

## Impact

- Analysis detail and history DTO/OpenAPI contracts gain `canRetry`; history changes the meaning of `retryable`.
- `SchemaDraftWorkflowNavigationService` must stop deriving `retryable` as command eligibility and expose a shared eligibility decision instead.
- `SchemaDraftAnalysisService.retry` must apply the same deterministic eligibility rules without treating persisted `retryable=false` as a rejection reason.
- Controller and integration coverage must distinguish transient-failure classification, completed-run reanalysis, permanent failures, closed drafts, missing active sources, and concurrent runs.
- Frontend clients must use `canRetry`, not `retryable`, when deciding whether to expose retry from detail or history.
