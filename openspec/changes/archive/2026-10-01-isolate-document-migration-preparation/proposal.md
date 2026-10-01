## Why

Reprocessing plan creation currently knows document repositories, processing
options, chunk classification, and chunker snapshot machinery. Moving those
responsibilities behind document capabilities completes the boundary started by
execution/recovery isolation without moving reprocessing policy out of schemas.

## What Changes

- Add schemas-owned preparation/read ports with integration adapters calling
  document-owned selection, classification, and input-preparation capabilities.
- Move document-specific snapshot construction, parser/option resolution, chunk
  presence checks, and completed-run revision comparison into documents.
- Keep schema target validation, plan selection policy, preview aggregation,
  destructive-plan exclusion, claims, retries, and plan persistence in schemas.
- Use the preparation capability for preview, creation, retry, and applicable
  target-currentness checks while preserving existing snapshots and transactions.
- Remove preparation exceptions introduced by change 1 and enforce the completed
  reprocessing/documents boundary.
- Update affected portal and contributor documentation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: enforce document ownership for reprocessing
  preparation and target inspection, completing the execution/recovery boundary.

The existing `chunk-strategy-reprocessing` and `schema-reprocessing-plans`
requirements remain unchanged, including immutable targets, preview/create
agreement, ownership safety, retries, and shared destructive-plan exclusion.

## Impact

Primary implementation paths are `selectedDocuments`, `chunkCandidates`,
`evaluateChunkMigration`, `isOutdated`, `targetStillActive`, and plan creation/retry
inside `SchemaReprocessingPlanService`. Documents capabilities reuse the existing
option resolver, chunking service, document/chunk/run repositories, and snapshot
semantics. No new HTTP API, persisted JSON format, database migration, worker,
build module, or processing algorithm is proposed.

This is roadmap change 2 in [the high-level design](../../../docs/MODULARIZATION_DESIGN.md).
Implementation requires `isolate-reprocessing-execution-recovery` first. Its
contracts can be extended additively but must retain change 1's behavior. General
AI compatibility isolation, other schema workflows, full feature relocation,
and performance/query-strategy changes are outside this slice.
