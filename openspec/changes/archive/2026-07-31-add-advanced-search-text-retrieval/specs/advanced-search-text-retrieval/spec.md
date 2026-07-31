## ADDED Requirements

### Requirement: Dense retrieval is reusable and isolated
The system SHALL batch-embed bounded advanced-search subqueries using the knowledge base active AI profile and SHALL retrieve only child chunks from the compatible knowledge-base embedding-space index.

#### Scenario: Other knowledge bases have stronger vector matches
- **WHEN** dense retrieval runs for a selected knowledge base
- **THEN** candidates from other knowledge bases do not consume its requested candidate budget

#### Scenario: Multiple subqueries are provided
- **WHEN** a bounded set of subqueries is submitted
- **THEN** the system embeds them in one supported batch and retains the originating subquery and raw rank for every result

### Requirement: Lexical retrieval uses source-safe child text
The system SHALL query a deterministic knowledge-base-scoped full-text index over unprefixed child `sourceText` using escaped, bounded phrase and term variants.

#### Scenario: Contextual embedding headers are enabled
- **WHEN** lexical retrieval searches a child whose vector text contains a contextual header
- **THEN** only its unprefixed source text contributes to full-text matching and returned citation text

#### Scenario: Exact identifier is searched
- **WHEN** a subquery contains an exact phrase or identifier
- **THEN** the lexical branch executes a boosted bounded variant without treating it as a separate retrieval family

### Requirement: Lexical indexes support existing chunks
The system SHALL idempotently label existing owned child chunks, create the configured full-text index, and wait within the operation deadline for the index to become online.

#### Scenario: Advanced search first runs on a legacy corpus
- **WHEN** owned child chunks predate lexical labeling
- **THEN** the lexical branch makes them searchable without document reprocessing

### Requirement: Metadata retrieval is relational and scoped
The system SHALL match explicit filename and content-type constraints against PostgreSQL document metadata and SHALL return only documents owned by the selected knowledge base.

#### Scenario: Same filename exists in two knowledge bases
- **WHEN** metadata retrieval runs for one knowledge base
- **THEN** only that knowledge base document and bounded chunks are eligible

### Requirement: Retriever results preserve channel diagnostics
Each text retriever SHALL return bounded candidates with source identity, raw channel rank and score, subquery identity, latency, and a sanitized completion or failure status.

#### Scenario: Embedding provider fails
- **WHEN** dense retrieval fails but lexical or metadata retrieval succeeds
- **THEN** the dense failure is reported without invalidating successful branch results
