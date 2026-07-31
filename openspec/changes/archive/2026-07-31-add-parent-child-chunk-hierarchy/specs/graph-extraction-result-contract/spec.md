## ADDED Requirements

### Requirement: Parent-scoped graph extraction
Graph extraction SHALL use a persisted bounded parent as its extraction unit and SHALL store that parent ID and its revisioned source scope as the authoritative source chunk for every derived evidence record.

#### Scenario: Fact extracted from parent
- **WHEN** graph extraction accepts a node or relationship from a parent context
- **THEN** its evidence references that parent and not an inferred child
