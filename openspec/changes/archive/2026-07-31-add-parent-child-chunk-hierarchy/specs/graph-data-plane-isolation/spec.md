## ADDED Requirements

### Requirement: Scoped hierarchy traversal
Every parent-child write and traversal SHALL validate matching knowledge base, document, retained processing run, and strategy revision, and SHALL exclude parent nodes from queries that test for or search embedded chunks.

#### Scenario: Mixed-revision parent reference
- **WHEN** a child references a parent from another strategy revision
- **THEN** persistence or expansion validation rejects the hierarchy
