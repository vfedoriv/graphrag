# parent-child-chunk-hierarchy Specification

## Purpose
Define authoritative parent-child chunk construction, retrieval isolation, and hierarchy lifecycle behavior.

## Requirements

### Requirement: Materialized parent and child hierarchy
The system SHALL construct bounded parent source spans and ordered contained child spans from the same tracked parser ranges, persist complete parent text directly from those ranges, and never reconstruct a parent by concatenating overlapping child strings.

#### Scenario: Parent with overlapping children
- **WHEN** a parent contains children whose retrieval windows overlap
- **THEN** the persisted parent text equals its authoritative source span exactly once and its hash is computed from that text

#### Scenario: Hierarchy identity
- **WHEN** a hierarchy is persisted successfully
- **THEN** every child references an existing parent with the same knowledge base, document, processing run, and strategy revision

### Requirement: Bounded cross-page PDF parents
A PDF parent MAY span at most two consecutive pages only when explicit compatible structural continuity and all token/page bounds are satisfied; every child and text-retrieval source span SHALL remain page-bounded.

#### Scenario: Compatible continuation
- **WHEN** consecutive PDF pages share accepted structural continuity and fit the configured parent bounds
- **THEN** one parent may record the ordered start/end page range while its children remain individually page-bounded

#### Scenario: Ambiguous continuation
- **WHEN** continuity is absent or ambiguous
- **THEN** parent construction stops at the page boundary

### Requirement: Child-only retrieval indexes
The system SHALL embed and index `CHILD` chunks only and SHALL persist `PARENT` chunks without an embedding or dense/lexical retrieval index membership.

#### Scenario: Parent persisted
- **WHEN** a parent-child hierarchy is written
- **THEN** the parent has complete bounded source text but no embedding and cannot be returned as a direct dense or lexical candidate

### Requirement: Atomic hierarchy lifecycle
Processing, overwrite, retry, replacement, deletion, and cleanup SHALL treat a parent and all its children as one document-scoped hierarchy and SHALL not expose a successful state containing orphan children or mixed revisions.

#### Scenario: Replacement succeeds
- **WHEN** document replacement completes under a new hierarchy revision
- **THEN** all prior parents, children, embeddings, evidence, relationships, and obsolete facts for that document are removed

#### Scenario: Persistence fails
- **WHEN** hierarchy persistence fails before the success checkpoint
- **THEN** the attempt remains recoverable and no partial hierarchy is reported as successfully processed
