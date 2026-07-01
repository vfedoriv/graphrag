## Why

The archived `harden-neo4j-persistence-state-handling` change added Spring Data Neo4j version metadata to assigned-id application entities. Its design assumed existing persisted nodes without the new metadata could be loaded and would receive version metadata on subsequent saves.

Profile timeout updates against existing `AiProfile` nodes disproved that assumption: Spring Data Neo4j can fail the first update with `OptimisticLockingFailureException` when the required version metadata is missing.

## What Changes

- Treat missing persistence version metadata on existing assigned-id Neo4j application nodes as a compatibility condition that must be normalized before versioned save/update paths run.
- Backfill missing `version` metadata for versioned application nodes and missing `entityVersion` metadata for `SchemaDefinition` nodes at startup.
- Preserve public API behavior, business identifiers, graph labels, relationships, and existing persisted business values.
- Cover the compatibility path with a Neo4j-backed AI profile update regression test.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `neo4j-persistence-state-handling`: Clarify that existing persisted application nodes without persistence version metadata must be backfilled before normal versioned persistence operations depend on that metadata.

## Impact

- Affected API: AI profile timeout updates and other existing entity update paths remain compatible with data created before persistence version metadata existed.
- Affected backend code: startup persistence version backfill service and Neo4j-backed integration coverage.
- Affected data: existing Neo4j application nodes may receive a missing `version` or `entityVersion` property during application startup.
- Dependencies: no new external dependencies.
