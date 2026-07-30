## ADDED Requirements

### Requirement: Recursive token-aware splitting
For newly processed documents using the recursive strategy, the system SHALL pack the largest complete structural units that fit the target token budget and fall back through paragraph, line, supported sentence, word, and character boundaries.

#### Scenario: English prose
- **WHEN** known-English content exceeds the target and contains usable paragraph, line, and sentence boundaries
- **THEN** the strategy prefers those complete units before word or character fallback

#### Scenario: Non-English content
- **WHEN** content is not known to be English
- **THEN** the strategy skips the English sentence tier and deterministically uses paragraph, line, word, and character boundaries

#### Scenario: Unbroken input
- **WHEN** a URL, identifier, or other unbroken string exceeds the token or hard character limit
- **THEN** the final character fallback produces bounded chunks

### Requirement: Token-bounded complete-unit overlap
The recursive strategy SHALL measure target size and overlap using the resolved token estimator and SHALL duplicate complete trailing units where possible without exceeding configured token and character guards.

#### Scenario: Configured overlap
- **WHEN** multiple chunks are required and complete trailing units fit the overlap budget
- **THEN** the following chunk begins with those units and diagnostics report token overlap rather than character overlap

### Requirement: Exact child source provenance
Each recursive child SHALL retain exact source text, reliable section-relative offsets, section and page order, structural path, block confidence, token count, source hash, strategy revision, tokenizer identity, and deterministic identity.

#### Scenario: Repeated text and overlap
- **WHEN** repeated text occurs inside overlapping chunks
- **THEN** every child reports the source ranges tracked during construction with no ambiguous reverse matching

#### Scenario: PDF child
- **WHEN** recursive splitting processes a page-aware PDF section
- **THEN** each child remains within one authoritative page boundary

### Requirement: Fixed strategy remains available
The system SHALL retain the versioned fixed strategy for controlled comparison and rollback while recursive chunking becomes the default for subsequent processing.

#### Scenario: Existing document
- **WHEN** the default changes to recursive
- **THEN** an already processed document remains on its persisted fixed revision until an explicit overwrite or reprocessing operation
