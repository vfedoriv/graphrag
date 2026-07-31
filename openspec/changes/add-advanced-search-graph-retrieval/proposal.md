## Why

Advanced search needs relationship and aggregation answers that text similarity alone cannot reliably retrieve. Reusing unrestricted model-generated Cypher would create an unnecessarily broad execution surface and would not guarantee evidence-backed citations.

## What Changes

- Add a typed graph-plan intermediate representation for schema labels, relationships, filters, projections, ordering, and bounded aggregations.
- Validate every plan element against the active knowledge-base schema and fixed operator/limit policies.
- Render only parameterized, knowledge-base-scoped Cypher with at most two typed relationship hops.
- Resolve every public graph fact through extraction evidence to its persisted parent chunk citation.

## Capabilities

### New Capabilities

- `advanced-search-graph-retrieval`: Schema-safe structured graph retrieval with direct KB scope and evidence-backed parent citations.

### Modified Capabilities

None.

## Impact

This adds graph-plan domain contracts, validators, a constrained renderer/executor, graph fact response models, and Neo4j integration tests. It reuses active-schema resolution and graph provenance but does not alter `/queries/ask` or accept arbitrary Cypher.
