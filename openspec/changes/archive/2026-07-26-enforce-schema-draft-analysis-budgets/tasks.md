## 1. Captured Execution Policy

- [x] 1.1 Add a typed `DiscoveryExecutionPolicy` snapshot containing per-run source concurrency, source timeout, request timeout, and canonical settings fingerprint.
- [x] 1.2 Resolve the policy once during draft run creation, persist additive nullable policy fields or canonical JSON, and pass the same immutable snapshot to background processing.
- [x] 1.3 Add effective budget metadata to analysis run detail/history responses with compatibility handling for legacy runs that have no snapshot.
- [x] 1.4 Add persistence, DTO/OpenAPI, and runtime-settings tests proving active runs retain their captured policy while subsequent runs use updated or cleared live settings.

## 2. Bounded Source Task Orchestration

- [x] 2.1 Extract content-safe source task input/result types that cover snapshot validation, preparation, reusable-result reconstruction, and unresolved candidate analysis without persisting inside worker tasks.
- [x] 2.2 Add a short-lived per-run source executor bounded by the captured source concurrency and kept separate from the global draft-run executor.
- [x] 2.3 Coordinate task results through completion order while tracking queued, started, accepted, timed-out, and canceled state for every source snapshot.
- [x] 2.4 Persist accepted source outcomes incrementally from the coordinator and guarantee exactly one terminal outcome per source.
- [x] 2.5 Add concurrency tests proving the per-run limit, global-run/product bound, queue behavior, mixed reusable/unresolved handling, and absence of recursive-executor deadlock.

## 3. Deadline Enforcement and Cancellation

- [x] 3.1 Start the request deadline at successful run claim and each source deadline at the task's actual start so queue waiting does not consume the source budget.
- [x] 3.2 Add atomic acceptance fencing that closes expired sources with distinct retryable source-deadline or request-deadline decisions and rejects all late results.
- [x] 3.3 Request interruption/cancellation for expired work, close queued and running tasks when the request deadline expires, and shut down the per-run executor without blocking application shutdown.
- [x] 3.4 Integrate remaining-budget checks with invalid-output retry so no additional output attempt starts after a source or request deadline.
- [x] 3.5 Emit privacy-safe warnings when the configured provider timeout/retry envelope can outlast the captured workflow budget without mutating the AI profile.
- [x] 3.6 Add deterministic tests for source timeout, queued-source request timeout, late success after cancellation, provider interruption resistance, request interruption, and complete terminal source counts.

## 4. Deterministic Aggregation and Durable Retry

- [x] 4.1 Store successful analyses by source snapshot identity and rebuild aggregate input in captured snapshot order rather than completion order.
- [x] 4.2 Add tests proving equivalent aggregate schema/candidates/conflicts/warnings across different source completion orders.
- [x] 4.3 Add integration coverage proving polling exposes completed durable outcomes during an active run and never exposes unfinished sources as terminal.
- [x] 4.4 Add retry coverage proving successful timed-run outcomes are reused and timed-out sources execute under the new run's captured policy.
- [x] 4.5 Verify startup recovery remains compatible with policy-bearing and legacy runs and does not attempt to resume an old captured client or executor.

## 5. Operational Verification

- [x] 5.1 Add content-safe run/source scheduling diagnostics with captured budgets, elapsed time, task state, cancellation requested, accepted/late result counts, and concurrency bounds.
- [x] 5.2 Run focused schema-draft analysis, workflow navigation, runtime-settings, DTO/OpenAPI, recovery, observability, and captured-log tests.
- [x] 5.3 Run `./mvnw test`.
- [x] 5.4 Run `graphify update .` and review the updated scheduling, deadline, reuse, and failure paths.
