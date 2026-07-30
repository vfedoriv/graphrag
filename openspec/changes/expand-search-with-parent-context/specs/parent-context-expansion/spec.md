## ADDED Requirements

### Requirement: Scope-validated parent expansion
The system SHALL expand a ranked child candidate only by loading a parent with matching knowledge base, document, retained processing run, strategy revision, and child-parent membership.

#### Scenario: Valid child parent
- **WHEN** a ranked child has a valid persisted parent in scope
- **THEN** the hit retains child ranking and evidence identity and gains a separately identified parent context

#### Scenario: Invalid parent scope
- **WHEN** any required scope or revision field differs
- **THEN** the parent is not exposed and the validation failure is recorded without content

### Requirement: Bounded deduplicated context
Parent expansion SHALL enforce typed limits for total context tokens, parent count, evidence count, and per-document contribution and SHALL avoid adding the same parent text repeatedly for sibling child hits.

#### Scenario: Sibling hits share a parent
- **WHEN** multiple retained children reference one parent
- **THEN** their individual evidence remains available while synthesis context includes that parent at most once

#### Scenario: Expansion exceeds budget
- **WHEN** eligible parents exceed an expansion limit
- **THEN** lower-priority context is omitted deterministically without changing child candidate scores

### Requirement: Constrained adjacency fallback
Adjacent-child expansion SHALL be a fallback for a missing legacy parent and SHALL remain inside the same document and compatible structural scope; hierarchical adjacency SHALL remain within the same parent.

#### Scenario: Legacy flat child
- **WHEN** a retained legacy child has no parent
- **THEN** only bounded adjacent chunks with compatible document, section, and revision metadata may provide fallback context
