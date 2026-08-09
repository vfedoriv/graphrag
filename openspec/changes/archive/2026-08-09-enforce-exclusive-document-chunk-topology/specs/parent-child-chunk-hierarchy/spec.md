## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Atomic hierarchy lifecycle
Processing, overwrite, retry, replacement, deletion, and cleanup SHALL treat all chunks as one document-scoped topology and SHALL not expose a successful state containing a mixed flat/hierarchical population, orphan children, unsupported chunk kinds, or mixed revisions.

#### Scenario: Replacement succeeds
- **WHEN** document replacement completes under a new topology or hierarchy revision
- **THEN** all prior parents, children, embeddings, evidence, relationships, and obsolete facts for that document are removed
- **AND** the successful replacement contains exactly one valid empty, flat, or hierarchical topology

#### Scenario: Persistence fails
- **WHEN** topology persistence fails before the success checkpoint
- **THEN** the attempt remains recoverable and no partial or mixed topology is reported as successfully processed
