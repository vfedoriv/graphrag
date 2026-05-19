## Why

The last commit added review summaries that identify real risks in extraction-run cleanup, schema validation, and graph extraction result handling. The highest-value fixes are to prevent cleanup failures from corrupting successful extraction state, make cleanup behavior observable and regression-tested, and reject schema definitions that would produce `"null"`-keyed extracted nodes.

## What Changes

- Harden extraction-run cleanup so a failure during post-success cleanup does not retroactively mark the current extraction run as `FAILED`.
- Make cleanup diagnostics explicit: use typed cleanup counters, warn when the cleanup query returns no row, and assert cleanup counters in integration coverage.
- Scope cleanup Cypher more clearly and safely, including deletion of run relationships via explicit `UNWIND` and deletion of obsolete extracted nodes only within the intended document-owned extracted graph data.
- Add regression coverage for successful overwrite cleanup, multiple failed runs, retained provenance edges, retained domain relationships, and deleted relationship counters.
- Replace raw extraction run status strings in Java code with an `ExtractionRunStatus` enum while preserving the persisted values used by existing Cypher.
- Normalize `GraphExtractionResult` null collections to empty immutable lists at construction time so consumers do not need scattered null guards.
- Validate each schema node key against that node's declared properties and reject invalid schema definitions before persistence or activation.
- Persist a non-blank extraction run error message even when an exception has no message.

## Capabilities

### New Capabilities
- `schema-definition-validation`: Covers semantic validation rules for accepted schema definitions, including node key/property consistency.
- `graph-extraction-result-contract`: Covers normalized graph extraction result payload semantics used before validation and persistence.

### Modified Capabilities
- `extraction-run-cleanup`: Adds cleanup observability and failure-isolation requirements, and tightens regression coverage expectations for overwrite, retained provenance, retained graph relationships, and multi-failed-run cleanup.

## Impact

- Affected code: `GraphExtractionService`, `ExtractionRunNode`, a new `ExtractionRunStatus` enum, `GraphExtractionResult`, `SchemaValidator`, and cleanup/schema/extraction tests.
- API impact: schema validation and schema creation may reject definitions that previously passed despite an invalid node key. This is intentional validation tightening, not a response-shape change.
- Persistence impact: extraction run status values remain `RUNNING`, `COMPLETED`, and `FAILED`; only Java typing changes.
- Operational impact: cleanup anomalies become visible in logs and cleanup counters become directly testable.
