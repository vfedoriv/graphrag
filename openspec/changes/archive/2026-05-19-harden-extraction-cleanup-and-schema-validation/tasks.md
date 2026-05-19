## 1. Extraction Run Typing

- [x] 1.1 Add `ExtractionRunStatus` enum with `RUNNING`, `COMPLETED`, and `FAILED` values.
- [x] 1.2 Change `ExtractionRunNode.status` from `String` to `ExtractionRunStatus`.
- [x] 1.3 Replace Java call sites that set or compare extraction run status strings with enum constants.
- [x] 1.4 Verify existing Cypher predicates still match persisted enum names.

## 2. Cleanup Result And Failure Isolation

- [x] 2.1 Add a typed `CleanupResult` record for deleted run, relationship, and obsolete-node counters.
- [x] 2.2 Change `cleanupRunsAfterCompletion` to return `CleanupResult` instead of `Map<String, Object>`.
- [x] 2.3 Log a warning when cleanup returns no row instead of silently treating the result as verified zero counters.
- [x] 2.4 Move post-success cleanup into its own `try/catch` so cleanup failures are logged but do not change the current run from `COMPLETED` to `FAILED`.
- [x] 2.5 Ensure extraction failures still persist the current run as `FAILED` with a non-blank fallback error message when `Exception.getMessage()` is null.

## 3. Cleanup Cypher Hardening

- [x] 3.1 Replace deleted-run relationship cleanup with explicit `UNWIND runsToDelete AS runToDelete` scoping.
- [x] 3.2 Remove unnecessary raw collection null filtering and redundant projected values from cleanup Cypher where safe.
- [x] 3.3 Scope obsolete-node deletion to extracted graph nodes for the target document and exclude infrastructure nodes.
- [x] 3.4 Rename cleanup counter semantics from orphan nodes to obsolete extracted nodes in code, Cypher aliases, and logs.

## 4. Schema And Extraction Result Validation

- [x] 4.1 Add a compact constructor to `GraphExtractionResult` that normalizes null `nodes` and `relationships` to `List.of()`.
- [x] 4.2 Update logging and validation paths to rely on normalized result collections instead of null checks.
- [x] 4.3 Add schema validator logic requiring each node `key` to be declared in that node's `properties`.
- [x] 4.4 Add clear schema validation errors for missing or empty property lists when a node key cannot be matched.

## 5. Regression Tests

- [x] 5.1 Extend cleanup integration tests to assert deleted cleanup counters, especially deleted relationships.
- [x] 5.2 Add cleanup coverage for two or more failed runs before a successful retry.
- [x] 5.3 Add overwrite cleanup coverage proving stale completed-run nodes and stale domain relationships are deleted while the new run's nodes and relationships survive.
- [x] 5.4 Assert shared nodes keep retained `CREATED_NODE` provenance after stale-run cleanup.
- [x] 5.5 Assert retained-run domain relationships survive cleanup.
- [x] 5.6 Add coverage that cleanup failure after a completed extraction does not change the current extraction run status to `FAILED`.
- [x] 5.7 Add unit tests for `GraphExtractionResult` null-list normalization.
- [x] 5.8 Add schema validator tests for valid node keys, missing key properties, and empty property lists.

## 6. Verification

- [x] 6.1 Run `./mvnw test`.
- [x] 6.2 Run `openspec validate harden-extraction-cleanup-and-schema-validation --strict`.
