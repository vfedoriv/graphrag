## ADDED Requirements

### Requirement: Graph requests use a closed typed plan
The system SHALL represent advanced-search graph requests as typed node filters, typed relationship hops, projections, ordering, and bounded aggregations rather than executable model-generated Cypher.

#### Scenario: Plan contains an unsupported operation
- **WHEN** a graph request includes an operation outside the typed allowlist
- **THEN** validation rejects it before rendering or execution

### Requirement: Graph plans are active-schema constrained
The system SHALL validate every label, relationship type, property, operator, literal type, hop count, projection, ordering field, and limit against the selected knowledge base active schema and runtime policy.

#### Scenario: Unknown relationship type is requested
- **WHEN** a graph plan names a relationship type absent from the active schema
- **THEN** the request is rejected with a sanitized validation outcome

#### Scenario: Graph plan requests more than two hops
- **WHEN** a graph plan exceeds the configured maximum of two typed hops
- **THEN** it is rejected before Cypher rendering

### Requirement: Rendered graph queries are parameterized and scoped
The system SHALL render only read-only template-owned Cypher whose literals are parameters and whose facts are directly constrained through extraction evidence for the selected knowledge base.

#### Scenario: Identical fact exists in another knowledge base
- **WHEN** graph retrieval runs for one knowledge base
- **THEN** evidence and facts owned by another knowledge base are excluded before the row limit is applied

### Requirement: Graph facts resolve authoritative citations
Every returned graph fact SHALL identify its canonical fact ID, schema representation, supporting evidence IDs, and persisted extraction-parent chunk citations.

#### Scenario: Evidence source parent is valid
- **WHEN** a graph fact is supported by scoped extraction evidence
- **THEN** its public citation identifies the persisted extraction parent and bounded source range

#### Scenario: Evidence parent cannot be resolved safely
- **WHEN** evidence lacks a valid same-scope persisted parent
- **THEN** the fact is omitted or marked unusable and no inferred child citation is created

### Requirement: Graph retrieval failure is branch-local
The graph retriever SHALL return bounded content-free diagnostics when validation, execution, timeout, or provenance resolution fails.

#### Scenario: Neo4j graph query times out
- **WHEN** the graph branch exceeds its available deadline
- **THEN** it terminates with a timeout status without invalidating independently retrieved text evidence
