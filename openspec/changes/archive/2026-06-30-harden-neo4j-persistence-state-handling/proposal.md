## Why

Normal document processing and related startup workflows can trigger Spring Data Neo4j warnings when application nodes use assigned business identifiers without framework-supported state detection. The same runtime-setting warning was already fixed for one node type; this change generalizes that persistence hygiene across Neo4j application entities and repository write paths.

## What Changes

- Add a cross-cutting persistence requirement for assigned-id Neo4j entities to use stable Spring Data Neo4j state handling.
- Ensure normal save/update workflows do not emit assigned-id new-entity warnings for application domain nodes.
- Avoid custom repository method signatures that cause Spring Data projection metadata noise for primitive `boolean` and `void` return types.
- Preserve existing API contracts, domain identifiers, graph labels, relationships, and business behavior.

## Capabilities

### New Capabilities
- `neo4j-persistence-state-handling`: Defines persistence mapping and repository contract expectations for warning-free Spring Data Neo4j handling of assigned-id application entities.

### Modified Capabilities

## Impact

- Affected backend code: Neo4j domain nodes with assigned ids, Neo4j repository interfaces, and service branches that consume repository existence checks.
- Affected storage behavior: newly saved assigned-id application nodes include Spring Data Neo4j-managed version metadata where applicable; existing business identifiers and relationships remain unchanged.
- Affected tests: focused unit tests for touched services and Neo4j-backed integration tests that exercise document processing, schema, knowledge base, and AI profile persistence.
- Affected API: no request or response shape changes are intended.
- Dependencies: no new external dependencies.
