## Context

The extraction pipeline currently permits multiple successful (`COMPLETED`) extraction runs for the same document, which leaves duplicate run roots and historical relationships in Neo4j. Existing cleanup behavior in `extraction-run-cleanup` focuses on removing failed runs after a successful retry, but it does not enforce a single retained successful run. The API also does not require explicit user confirmation before replacing an already successful extraction result.

Constraints:
- Cleanup must run only after the new run is durably marked `COMPLETED`.
- Deletion scope must be limited to the target document.
- Shared graph nodes must be preserved when still connected to retained nodes.
- Existing extraction trigger API and tests must remain backward-compatible except for the new overwrite guard behavior.

## Goals / Non-Goals

**Goals:**
- Require explicit user consent (`allowOverwrite=true`) before starting a new extraction when a completed run already exists for a document.
- After a successful overwrite run, delete prior completed extraction run nodes for that same document.
- Remove relationships attached to deleted run nodes and then remove orphaned nodes left with zero relationships.
- Keep behavior deterministic and safe under retries.

**Non-Goals:**
- Preserve historical successful run lineage for audit/reporting.
- Introduce soft-delete or archival storage for replaced runs.
- Change extraction behavior for documents that have no prior completed run.

## Decisions

1. API contract adds `allowOverwrite` boolean, defaulting to `false`.
- Rationale: safe-by-default behavior prevents accidental replacement of completed data.
- Alternative considered: always overwrite implicitly.
- Rejected because it hides destructive behavior and removes user intent confirmation.

2. Pre-flight gate checks for existing completed run by document before launching a new extraction.
- Rationale: fail fast with clear validation error before expensive processing.
- Alternative considered: run extraction and block only at persistence time.
- Rejected because it wastes compute and complicates rollback.

3. Cleanup prior completed runs only when current run succeeds and overwrite was allowed.
- Rationale: prevents deleting stable data if new run fails.
- Alternative considered: delete old completed runs before processing starts.
- Rejected because failure during processing would leave no completed run.

4. Cleanup query strategy uses document-scoped deletion of prior completed run nodes excluding the current run, then orphan sweep.
- Rationale: keeps cleanup bounded and predictable.
- Alternative considered: broad orphan cleanup over the whole graph.
- Rejected because global sweep is expensive and risks unintended deletes.

5. Orphan removal is relationship-count based after run deletion.
- Rationale: directly matches the requirement to delete nodes that only survived through deleted run relationships.
- Alternative considered: label-restricted orphan cleanup.
- Rejected because cleanup should apply regardless of node label if it became orphaned due to this operation.

## Risks / Trade-offs

- [Risk] Concurrent overwrite requests for the same document can race and both pass pre-flight checks.
- Mitigation: perform overwrite gate and post-success cleanup in transaction boundaries with document+run predicates; add integration coverage for near-concurrent requests.

- [Risk] Over-aggressive orphan cleanup might remove nodes that should be retained for other workflows.
- Mitigation: scope orphan sweep to nodes touched by deleted completed runs, not global database nodes.

- [Risk] API clients may break if they assume repeat extraction without explicit flags.
- Mitigation: return explicit error details explaining `allowOverwrite` requirement; update API docs/examples.

## Migration Plan

1. Add API/request model support for `allowOverwrite` defaulting to `false`.
2. Implement pre-flight overwrite guard in extraction trigger service path.
3. Extend cleanup service/repository to remove prior completed runs for same document after successful overwrite run.
4. Add orphan sweep limited to cleanup candidate nodes.
5. Add or update integration tests for guard behavior, successful overwrite cleanup, and orphan preservation/deletion cases.
6. Release with updated endpoint documentation and error contract examples.

Rollback strategy:
- Revert to prior code path that permits repeated extraction and failed-run-only cleanup.
- No schema migration required; behavior rollback is application-level.

## Open Questions

- None.
