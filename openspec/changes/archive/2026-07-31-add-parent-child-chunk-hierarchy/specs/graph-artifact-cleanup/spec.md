## ADDED Requirements

### Requirement: Hierarchy-aware graph cleanup
Document cleanup SHALL remove graph evidence and relationships that reference any parent or child before removing the complete document-scoped chunk hierarchy and obsolete extracted nodes.

#### Scenario: Document deletion
- **WHEN** an owned document with parent-child chunks is deleted
- **THEN** no parent, child, embedding, extraction evidence, document-scoped relationship, or obsolete extracted node remains
