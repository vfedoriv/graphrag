## Why

Running extraction multiple times for the same document currently leaves multiple `COMPLETED` extraction run nodes, which duplicates run history and keeps redundant relationships alive. We need deterministic overwrite behavior so the newest successful extraction becomes the single source of truth, with explicit user intent to replace prior successful results.

## What Changes

- Add explicit overwrite gating for extraction retries when a completed run already exists for the same document.
- Introduce a request parameter `allowOverwrite` (default `false`) that must be `true` to start a new extraction when prior successful extraction exists.
- On successful completion of a new extraction run started with overwrite approval, delete older completed extraction run nodes for that document.
- Remove all relationships attached to deleted completed run nodes.
- Remove nodes that become orphaned due to this cleanup (nodes with no remaining relationships after run deletion).
- Keep shared nodes that still have relationships to retained graph structures.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `extraction-run-cleanup`: extend cleanup semantics from failed-run cleanup only to include overwrite cleanup of prior completed runs after a newly successful overwrite run, plus overwrite confirmation requirements.

## Impact

- Affected API: extraction trigger contract (new `allowOverwrite` parameter).
- Affected services: extraction orchestration and post-success cleanup flow.
- Affected persistence: Neo4j cleanup query scope and safeguards against unintended deletions.
- Affected tests: integration/service tests for overwrite gating, successful overwrite cleanup, and orphan-node removal behavior.
