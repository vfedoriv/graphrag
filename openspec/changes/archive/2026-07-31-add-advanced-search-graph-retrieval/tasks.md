## 1. Typed Graph Plan

- [x] 1.1 Define closed graph-plan variants for filters, typed hops, projections, ordering, aggregations, and limits.
- [x] 1.2 Implement active-schema and runtime-policy validation for every IR element.
- [x] 1.3 Unit-test invalid identifiers, operators, literal types, projections, limits, and hop counts.

## 2. Rendering and Provenance

- [x] 2.1 Implement centralized parameterized Cypher templates with direct evidence-based KB scope.
- [x] 2.2 Implement bounded graph execution and content-free branch diagnostics.
- [x] 2.3 Resolve node and relationship results through evidence `sourceChunkId` to validated persisted parent citations.
- [x] 2.4 Define graph fact results without inferred child citations or public executable Cypher.

## 3. Verification

- [x] 3.1 Neo4j integration-test node filters, two-hop relationships, aggregations, ordering, and row bounds.
- [x] 3.2 Test stronger competing cross-KB facts, missing provenance, stale parents, and timeouts.
- [x] 3.3 Run focused tests and `graphify update .`.
