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
Processing, overwrite, retry, replacement, deletion, and cleanup SHALL treat all chunks as one document-scoped topology and SHALL not expose a successful state containing a mixed flat/hierarchical population, orphan children, unsupported chunk kinds, or mixed revisions.

#### Scenario: Replacement succeeds
- **WHEN** document replacement completes under a new topology or hierarchy revision
- **THEN** all prior parents, children, embeddings, evidence, relationships, and obsolete facts for that document are removed
- **AND** the successful replacement contains exactly one valid empty, flat, or hierarchical topology

#### Scenario: Persistence fails
- **WHEN** topology persistence fails before the success checkpoint
- **THEN** the attempt remains recoverable and no partial or mixed topology is reported as successfully processed

### Requirement: Document chunk topology is exclusive
A successfully persisted document chunk snapshot SHALL be exactly one of: empty with no chunks, flat with no `PARENT` chunks and every `CHILD.parentChunkId` null, or hierarchical with at least one `PARENT` and every `CHILD` referencing a valid parent in the same knowledge base, document, processing run, and effective chunker revision. A snapshot SHALL NOT combine parent chunks with unparented child chunks.

#### Scenario: Fixed-character output is persisted
- **WHEN** fixed-character processing produces child chunks without parent chunks
- **THEN** the document is persisted as a valid flat topology
- **AND** every persisted chunk has `kind=CHILD` and a null `parentChunkId`

#### Scenario: Recursive output is persisted
- **WHEN** recursive processing produces parent and child chunks
- **THEN** the document is persisted as a valid hierarchical topology
- **AND** every child references a valid same-scope parent

#### Scenario: Mixed replacement input is supplied
- **WHEN** a replacement batch contains a parent chunk and an unparented `CHILD` chunk
- **THEN** persistence rejects the batch before deleting the existing document chunk snapshot
