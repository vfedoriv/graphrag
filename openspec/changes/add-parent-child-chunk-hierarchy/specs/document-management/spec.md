## ADDED Requirements

### Requirement: Hierarchical chunk reads
Document chunk reads SHALL distinguish `PARENT` and `CHILD`, expose parent identity and bounded source/page/structural scope, and return chunks in deterministic hierarchy and document order.

#### Scenario: Client reads hierarchy
- **WHEN** a client lists chunks for a hierarchically processed owned document
- **THEN** the response makes child-to-parent membership explicit without exposing parent embedding fields
