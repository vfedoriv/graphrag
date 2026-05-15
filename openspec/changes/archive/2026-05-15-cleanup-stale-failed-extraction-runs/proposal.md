## Why

When a failed extraction run is retried and later succeeds, the failed run node and its relationships remain in Neo4j. This creates duplicate run history in the graph, leaves stale links, and can leave orphaned nodes that are no longer relevant.

## What Changes

- Add post-success cleanup for prior failed extraction runs for the same document.
- Delete stale failed run nodes and all relationships attached to those run nodes after a successful run is persisted.
- Delete graph nodes that become isolated as a direct result of this cleanup.
- Keep cleanup scoped so nodes still connected to active/completed runs are preserved.
- Add tests to verify stale failed run cleanup and orphan-node cleanup behavior.

## Capabilities

### New Capabilities
- `extraction-run-cleanup`: automatic graph cleanup of prior failed extraction runs and newly orphaned nodes after a successful extraction run.

### Modified Capabilities
- None.

## Impact

- Affected code: graph persistence/cleanup flow in extraction processing and related repositories/Cypher.
- Data behavior: failed extraction runs no longer accumulate after successful retries for the same document.
- Stability: reduces graph noise and prevents buildup of stale and orphaned extraction artifacts.
