## Why

The supported application and API processing and replacement paths already write one document-scoped chunk topology at a time, and the current Neo4j data audit contains no invalid document populations. Direct Neo4j writes, repository bypasses, or manual imports can still create invalid populations because Neo4j does not enforce this document-level invariant. Keeping mixed parent/flat populations as a supported read contract adds behavior and tests for a state that no supported product write path creates or retains.

## What Changes

- Define successful document chunk output as exactly one topology: empty, pure flat, or pure parent-child hierarchy.
- **BREAKING** Stop guaranteeing bounded reads or hierarchy summaries for any invalid persisted topology, including mixed parent/flat populations, unsupported or null kinds, orphan references, and cross-run or cross-revision hierarchy references; treat those states as integrity violations.
- Retain virtual `kind=FLAT`, persisted response `kind=CHILD`, and `flatChunkCount` for legitimate fixed-character documents.
- Strengthen persistence and processing tests so replacement cannot leave a mixed topology and every hierarchical `CHILD` references a parent from the same document run/revision.
- Add a rollout audit that must report no invalid chunk populations or hierarchy references before the invariant is deployed.
- Replace mixed-population API fixtures with separate pure-flat and pure-hierarchy fixtures.
- Continue treating directly or manually created invalid populations as diagnosable persisted-state violations rather than supported product output; this change does not add a Neo4j topology constraint.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `parent-child-chunk-hierarchy`: Make flat and hierarchical document chunk topologies mutually exclusive across successful persistence, replacement, and cleanup.
- `document-management`: Retain bounded FLAT reads for pure-flat documents while removing invalid-population read guarantees and defining mixed and otherwise invalid persisted topologies as unsupported.

## Impact

This affects document chunk persistence validation, processing/replacement integrity checks, Neo4j audit coverage, bounded chunk and hierarchy service tests, controller integration fixtures, and the two modified capability specifications. Valid pure-flat and pure-hierarchy API requests keep their existing wire shapes; only behavior for invalid persisted topologies changes, including mixed populations, unsupported or null kinds, orphan references, and cross-run or cross-revision hierarchy references. Frontend behavior changes are handled separately.
