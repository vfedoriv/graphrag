## Context

`SchemaDraftAnalysisRunNode.retryable` is persisted from terminal source outcomes: it is true when at least one failed outcome has a transient recovery decision and false for completed runs or runs containing only permanent failures. Analysis detail returns this persisted value.

Analysis history currently places a different value in the identically named field. It derives whether the selected terminal run can generally be used as the parent of another analysis from draft openness and concurrent-run state. The retry endpoint follows that command-oriented model and intentionally permits completed-run reanalysis, which is used to reuse successful outcomes while capturing a new AI profile, settings, source membership, or other snapshot input.

The archived workflow-navigation design made this semantic split intentional, but using one JSON property for both concepts is unsafe for clients. The current history derivation is also only a partial approximation of command eligibility because it omits active-source availability and is not shared directly with retry validation.

## Goals / Non-Goals

**Goals:**

- Give `retryable` one stable meaning across detail and history: persisted failure classification.
- Expose retry-command availability independently as `canRetry`.
- Preserve completed-run and permanent-failure reanalysis when current resource state allows it.
- Share deterministic resource-state eligibility between response projection and command validation.
- Keep retry validation authoritative in the presence of stale responses and races.
- Provide explicit migration and test coverage for frontend consumers.

**Non-Goals:**

- Automatically retry analyses.
- Guarantee retry acceptance based solely on a previously read `canRetry` value.
- Fold transient executor queue capacity or downstream provider health into a stable response flag.
- Change source failure classification or persisted run retryability calculation.
- Change source-result reuse keys, lineage semantics, or analysis-start idempotency.
- Add a new API version solely for this contract correction.

## Decisions

### Keep `retryable` as failure metadata and add `canRetry`

Both analysis detail and history will return:

- `retryable`: the persisted run-level aggregation of retryable source or interruption failures.
- `canRetry`: deterministic command eligibility based on current resource state.

This preserves the established detail meaning and aligns run-level retryability with per-source recovery decisions. History changes semantics and is therefore called out as breaking for consumers even though its JSON type remains boolean.

Alternative considered: retain endpoint-specific meanings and document them. Rejected because generic clients, generated types, and UI components cannot safely infer different meanings from the same property name.

Alternative considered: rename the persisted field to `failureRetryable` and remove `retryable`. Rejected for now because it increases response churn without improving the distinction beyond pairing the established field with `canRetry`.

### Treat failure classification as diagnostic, not authorization

The retry command will not reject a run merely because persisted `retryable` is false. A completed run may be deliberately reanalyzed to capture changed execution inputs, while a permanent source failure may become resolvable after source or draft state changes. A child retry continues to record the selected parent and uses normal reuse-key rules.

Alternative considered: permit retry only for `PARTIAL` or `FAILED` runs with `retryable=true`. Rejected because it breaks existing completed-run reanalysis and conflates whether the original failure was transient with whether a new analysis is currently meaningful.

### Centralize deterministic retry eligibility

A service-level eligibility function will evaluate a selected run against current draft state. At minimum, eligibility requires:

- the selected run is terminal;
- the draft is open;
- the draft has at least one active eligible source; and
- no analysis is currently running for the draft.

History will calculate draft-wide inputs once per page and apply the function to each run. Detail will use the same function for its selected run. Retry will re-evaluate the same rules before delegating to normal synchronized start logic. The command remains authoritative and normal start validation continues to handle optimistic revision checks, profile resolution, queue capacity, reservation races, and matching-run idempotency.

The helper should return an internal typed decision or reason rather than an unstructured boolean so command failures can remain precise without adding a public reason field in this change.

Alternative considered: make `canRetry` mirror every condition that could make the command fail. Rejected because queue capacity, concurrent races, and provider availability are transient; representing them in a response boolean would imply a guarantee the system cannot provide.

### Derive `canRetry`; do not persist it

Command eligibility changes when the draft closes, sources change status, or another run starts. Persisting it on historical runs would immediately become stale and would require broad multi-row updates. The value will be derived at read and command time, like analysis currentness.

Alternative considered: update every historical run whenever eligibility changes. Rejected because it creates avoidable writes and race windows while making historical failure metadata harder to distinguish from current action state.

### Update the API contract and migration guidance together

DTOs and generated OpenAPI metadata will expose `canRetry` on analysis detail and history summaries. Existing frontend actions must migrate from history `retryable` to `canRetry`; failure messaging and diagnostics continue to use `retryable`.

Contract and integration tests will assert both values in combinations that previously looked contradictory, including completed runs, retryable partial failures, permanent failures, closed drafts, missing active sources, and concurrent runs.

## Risks / Trade-offs

- [Existing history consumers treat `retryable` as action eligibility] → Mark the semantic change as breaking, add `canRetry` explicitly, and update all in-repository consumers in the same implementation.
- [`canRetry=true` becomes stale before POST] → Document it as a current-state hint and revalidate authoritatively during the command.
- [Eligibility logic drifts between list, detail, and retry] → Use one typed service-level decision with shared tests rather than duplicating boolean expressions.
- [Adding active-source checks increases history query cost] → Resolve draft-wide active-source availability once per response instead of once per run.
- [A terminal run with `retryable=false` is mistaken as impossible to rerun] → Test and document that failure retryability is diagnostic and independent of `canRetry`.
- [Legacy persisted runs lack newer failure details] → Continue reading their stored boolean default while deriving `canRetry` exclusively from current state.

## Migration Plan

1. Add `canRetry` to analysis detail and history DTO/OpenAPI contracts.
2. Introduce the shared typed eligibility decision and use it in history and detail projections.
3. Apply the same decision in retry validation while preserving normal start-time race and capacity checks.
4. Change history `retryable` to return the persisted run value.
5. Update backend contract, service, and integration tests, then migrate frontend retry actions to `canRetry`.
6. Deploy backend and frontend together, or deploy the additive backend field first while ensuring no consumer exposes history retry based on the legacy field.
7. Roll back by restoring the history mapping and removing `canRetry`; no Neo4j data migration is required.

## Open Questions

None.
