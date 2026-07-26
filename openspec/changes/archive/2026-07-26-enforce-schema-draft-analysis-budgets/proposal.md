## Why

Durable schema-draft analysis records discovery concurrency and source/request timeout settings in its reuse fingerprint but currently processes sources sequentially without enforcing those budgets. As a result, one slow source can consume the full provider timeout and retry envelope before later sources start, while runtime settings appear effective even though they do not control this workflow.

## What Changes

- Persist and use a typed discovery execution-policy snapshot when a draft analysis run is created.
- Process unresolved draft sources with bounded per-run concurrency while retaining the existing global run executor and durable per-source progress.
- Enforce a per-source analysis deadline and an overall run request deadline, including preparation, candidate extraction, conversion, validation, and application-level output retries.
- Stop accepting late task results after their deadline, cancel unfinished work on a best-effort basis, and persist a terminal outcome for every source in the run snapshot.
- Preserve deterministic aggregate ordering and exact-match reuse regardless of source completion order.
- Make live discovery execution-setting changes affect subsequent runs without changing already captured runs.
- Expose effective run budget metadata and add privacy-safe scheduling/deadline diagnostics.
- Add concurrency, deadline, cancellation, reuse, determinism, runtime-setting, and restart/interruption regression coverage.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-analysis`: Require captured execution budgets, bounded concurrent source work, enforced source/request deadlines, durable timeout outcomes, and deterministic completion.
- `runtime-application-settings`: Require live schema-discovery concurrency and timeout settings to govern subsequent durable draft analysis runs through an immutable per-run policy snapshot.

## Impact

- `SchemaDraftAnalysisService`, draft analysis configuration, source task orchestration, run/result persistence, and workflow navigation DTOs.
- Typed discovery settings resolution and settings fingerprint/snapshot persistence.
- Interaction with the model-output retry and structured failure decisions defined by `harden-schema-draft-model-failures`, which should be implemented first.
- Additional bounded executors or task-coordination components; no external queue or new dependency is required.
- Additive run status metadata for effective concurrency and timeout budgets, with compatibility handling for legacy runs.
- Integration tests for concurrent source completion and Testcontainers-backed durable progress.
