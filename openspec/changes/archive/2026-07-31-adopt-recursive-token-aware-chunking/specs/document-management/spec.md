## ADDED Requirements

### Requirement: Rich flat chunk reads
Document chunk reads SHALL return authoritative child source text and queryable provenance including chunk kind, section/page order, source range when reliable, structural path, token count, strategy revision, and tokenizer identity without returning the synthetic embedding header as document text.

#### Scenario: Client lists recursive chunks
- **WHEN** a client retrieves chunks for an owned recursively processed document
- **THEN** chunks are in deterministic document order and expose exact source provenance plus revision metadata
