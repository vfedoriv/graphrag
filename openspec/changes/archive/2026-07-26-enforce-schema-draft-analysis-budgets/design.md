## Context

Schema-draft runs are submitted to a bounded `schemaDraftAnalysisExecutor`, which limits concurrent runs and queue depth. Inside a claimed run, however, `SchemaDraftAnalysisService` iterates source snapshots sequentially. The run's reuse fingerprint includes the typed `app.schema-discovery.max-concurrency`, `source-timeout-seconds`, and `request-timeout-seconds` values, but the draft workflow neither persists their concrete values nor enforces them.

The synchronous review-only discovery path already uses concurrent futures and deadlines, but its orchestration is not directly reusable: durable draft analysis must persist each accepted source outcome independently, reuse prior successes, preserve deterministic aggregation, survive partial failure, reject late results, and close every source snapshot with exactly one outcome.

This change follows `harden-schema-draft-model-failures`. Its timeout outcomes use that change's structured failure decisions, and its source deadline includes any bounded invalid-output retry introduced there.

## Goals / Non-Goals

**Goals:**

- Capture an immutable typed execution policy for each new draft run.
- Apply bounded per-run source concurrency without weakening the global run/queue bound.
- Enforce source deadlines from actual task start and a request deadline from run claim.
- Persist exactly one terminal result per source, including queued/running sources interrupted by the request deadline.
- Prevent timed-out or canceled late tasks from mutating durable outcomes.
- Preserve deterministic aggregates and exact-match reuse regardless of completion order.
- Make live discovery budget changes apply only to subsequently created runs.

**Non-Goals:**

- Replace the in-process durable-run model with a distributed queue.
- Guarantee that every provider HTTP client immediately terminates underlying network I/O after Java task cancellation.
- Add per-HTTP-attempt telemetry or replace provider SDK timeout/retry settings.
- Dynamically change the policy of a run that has already been created.
- Increase the configured source-count or payload limits.

## Decisions

### Persist a typed execution-policy snapshot

At run creation, resolve `RuntimeSettingsService.DiscoverySettings` once and construct a `DiscoveryExecutionPolicy` containing at least:

- maximum concurrent source tasks;
- per-source timeout;
- overall request timeout;
- the existing canonical settings fingerprint.

Persist the effective values or one canonical policy JSON snapshot on the run in addition to the fingerprint. Pass the same immutable value to the background work item. Run status responses expose the effective values additively so operators can distinguish runtime discovery deadlines from AI-profile HTTP timeout/retry settings.

Legacy terminal runs may have no policy snapshot and remain readable. Startup recovery already closes legacy/in-flight `RUNNING` runs, so a restarted process does not reconstruct an old execution policy to resume work.

Alternative considered: resolve live settings inside the worker or before every source. Rejected because a setting update could change scheduling midway through a run and make its persisted fingerprint inconsistent with actual execution.

### Keep separate global-run and per-run-source bounds

The existing `app.schema-draft.analysis.concurrency` and queue capacity continue to bound active durable runs. Each run creates a short-lived fixed-size source executor with size:

`min(captured max source concurrency, source snapshot count)`

The maximum simultaneous source calls for one application instance is therefore bounded by the product of active-run concurrency and captured per-run source concurrency. This relationship is documented and logged when a run starts.

All source snapshots, including reusable ones that still require current preparation, execute as source tasks. A task validates and prepares its source, reconstructs a reusable success or performs candidate analysis, and returns a content-safe result object. It does not persist its own terminal result.

Alternative considered: use the existing run executor recursively for source tasks. Rejected because runs waiting for their own child tasks can exhaust the same pool and deadlock.

Alternative considered: create one unbounded virtual thread per source. Rejected because provider concurrency, rather than Java thread cost, is the resource that must remain explicitly bounded.

### Coordinate completions and deadlines centrally

Use a completion-oriented coordinator rather than awaiting futures in source-list order. Each task records its actual start time. The coordinator repeatedly accepts completed tasks and checks:

- the run request deadline, measured from successful run claim;
- each started source task's deadline, measured from its task start;
- interruption or shutdown.

When a source deadline expires, the coordinator atomically closes that task as a retryable source timeout, requests cancellation/interruption, and ignores any later task result. When the request deadline expires, it atomically closes every unfinished queued or running source with a retryable request-deadline outcome, requests cancellation, and stops waiting.

An acceptance fence or compare-and-set terminal state ensures exactly one outcome wins for each source. Only the coordinator persists source results, so a provider call that ignores interruption cannot publish a late success after timeout.

Alternative considered: call `Future.get(sourceTimeout)` for each source in snapshot order. Rejected because later sources may finish while the coordinator waits on an earlier source, and a timeout measured at `get` time does not represent the task's actual execution duration.

### Treat orchestration deadlines as acceptance boundaries

The source/request deadlines govern when the durable workflow accepts a result. Cancellation is requested for expired work, but correctness does not depend on the underlying HTTP client immediately stopping. The AI profile timeout and SDK retry count remain provider-call settings.

Before starting an application-level invalid-output retry, the source task checks its remaining captured source and request budget. It does not start the extra output attempt after either deadline has expired. If the theoretical provider timeout/retry envelope exceeds the source or request budget, the run emits a privacy-safe configuration warning; it does not silently rewrite the AI profile.

Alternative considered: automatically lower the captured model client's HTTP timeout. Rejected because it would execute the run under different AI-profile behavior than its persisted profile revision and could affect other calls sharing the cached client.

### Persist progress in deterministic snapshot order

The coordinator may persist accepted source outcomes as they complete so polling shows incremental durable progress. For aggregation, it stores successful analyses by source snapshot identity and reconstructs the success list in the original snapshot order before invoking `DiscoveryAggregator`.

Completion order, timeout order, and thread scheduling therefore do not affect candidate aggregation or schema content. Created/completed timestamps remain operational facts and need not be deterministic.

Reusable outcomes still require the exact existing reuse key. Changing any captured discovery setting changes the settings fingerprint, making old results ineligible exactly as today.

### Keep timeout classification and logging content-safe

Source-deadline and request-deadline outcomes use separate detailed failure codes under the compatible broad timeout category. Logs and observations include run/source identifiers, captured budgets, elapsed time, queue/start/completion state, cancellation requested, and counts. They do not include source text, prompts, model responses, candidate payloads, raw exception messages, or credentials.

## Risks / Trade-offs

- [Parallel source calls can increase provider throttling] → Keep concurrency captured and bounded, use conservative defaults, and surface rate-limit outcomes through structured failure decisions.
- [Nested per-run executors multiply global provider concurrency] → Document and test the product bound of run concurrency and source concurrency; retain the existing global run limit.
- [Canceled HTTP work may continue briefly] → Make task cancellation best effort and enforce an acceptance fence so late work cannot persist or alter aggregation.
- [Concurrent completion can reorder persistence timestamps] → Reconstruct aggregate inputs in source snapshot order and test deterministic output across completion orders.
- [Request timeout can expire while tasks remain queued] → Close every unfinished source with a request-deadline outcome so run counts and retryability remain complete.
- [Policy snapshot adds nullable persisted fields] → Use additive properties and compatibility reads for legacy terminal runs; no backfill is required.
- [Very short live deadlines can create repeated partial runs] → Validate positive bounded values and expose effective budgets in run responses and warnings.

## Migration Plan

1. Implement `harden-schema-draft-model-failures` and its structured timeout decisions first.
2. Add the typed execution-policy snapshot and nullable persisted/API fields.
3. Introduce source task/result abstractions and central completion coordination behind the existing run executor.
4. Enforce source/request deadlines, acceptance fencing, cancellation, and deterministic aggregation.
5. Deploy with existing defaults and monitor provider concurrency, timeout, and partial-run metrics.
6. Roll back to sequential execution while retaining nullable policy fields. New timeout outcomes remain auditable and retryable; existing completed source results remain governed by their settings fingerprint.

## Open Questions

None. `maxConcurrency` is a per-run source bound, while `app.schema-draft.analysis.concurrency` remains the global active-run bound.

