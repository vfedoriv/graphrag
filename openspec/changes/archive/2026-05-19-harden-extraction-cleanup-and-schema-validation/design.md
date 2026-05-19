## Context

`GraphExtractionService` currently persists an extraction run as `COMPLETED` and then performs cleanup in the same `try` block used for extraction. If cleanup fails, the catch path changes the already-successful run to `FAILED`, which misrepresents the actual extraction outcome and can trigger unnecessary reprocessing. Cleanup also returns untyped map counters and silently converts an absent query result into zeros.

The review summaries also surfaced two type-safety gaps outside cleanup: `ExtractionRunNode.status` is a raw string despite having a small fixed lifecycle, and `GraphExtractionResult` allows null node/relationship collections. Schema validation accepts a node key that is not declared in the node properties, which can later cause extracted nodes to collapse into `"null"`-keyed records.

## Goals / Non-Goals

**Goals:**
- Preserve successful extraction run status when post-success cleanup fails.
- Make cleanup counters typed and observable enough for regression tests and operator diagnostics.
- Keep cleanup scoped to extraction artifacts for the target document.
- Replace extraction run status string usage in Java with enum-backed values.
- Normalize graph extraction result collections at construction boundaries.
- Reject schemas whose node key is not present in the node's property definitions.
- Add focused integration/unit coverage for the confirmed critical and important review issues.

**Non-Goals:**
- No database migration for existing extraction run status values; the string values remain unchanged.
- No API response-shape changes beyond stricter schema validation errors.
- No broad rewrite of graph persistence or extraction prompting.
- No attempt to implement every low-priority review suggestion.

## Decisions

1. Use an `ExtractionRunStatus` enum persisted as the existing string values.

   Rationale: this removes magic strings from Java paths while preserving current Cypher predicates and stored data. The alternative, leaving `String`, keeps typo risk in the most important cleanup predicates.

2. Split extraction failure handling from cleanup failure handling.

   Rationale: extraction and graph writes determine whether the current run succeeded. Cleanup is a follow-up maintenance action and must not retroactively rewrite the run outcome. The alternative, keeping one catch block, preserves the current misleading failure mode.

3. Introduce a small `CleanupResult` record for cleanup counters.

   Rationale: typed counters remove map-key typos and make tests assert the intended side effects directly. The alternative, `Map<String, Object>`, requires magic strings and `toLong` fallback behavior that can hide missing values.

4. Treat an empty cleanup query result as an anomaly, not a zero-count success.

   Rationale: the query should always return one row when invoked for a persisted completed current run. Logging a warning preserves runtime progress while making structural query regressions visible.

5. Prefer explicit Cypher scoping over broad matches with late filters.

   Rationale: `UNWIND runsToDelete AS runToDelete` makes deleted-run relationship cleanup clear and avoids a broad relationship expansion. Obsolete extracted-node deletion should remain constrained by document provenance and avoid infrastructure labels such as `ExtractionRun`, `DocumentUpload`, and `DocumentChunk`.

6. Normalize `GraphExtractionResult` lists in compact constructors.

   Rationale: null collections have no useful domain meaning and force every consumer to branch defensively. Normalizing to `List.of()` keeps downstream validation and logging simple.

7. Add schema key/property validation in `SchemaValidator`.

   Rationale: node keys drive stable extracted-node lookup. A key that is missing from properties should be rejected at schema ingestion time rather than failing indirectly during graph writes.

## Risks / Trade-offs

- Stricter schema validation may reject existing test fixtures or local schemas that currently pass with invalid keys. Mitigation: update fixtures to declare keys consistently and surface a clear validation message.
- Cleanup failure isolation can leave stale failed/completed runs until a later successful cleanup. Mitigation: log cleanup failures with document and run IDs; the next successful extraction can retry cleanup.
- Enum persistence depends on Spring Data Neo4j serializing enum names consistently. Mitigation: use enum names matching the existing persisted status values and verify with repository/integration tests.
- More specific cleanup Cypher can accidentally under-delete if provenance assumptions are wrong. Mitigation: add tests for failed-only nodes, shared nodes with retained provenance, stale domain relationships, retained domain relationships, overwrite cleanup, and multi-failed-run cleanup.
