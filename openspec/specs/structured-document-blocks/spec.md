# structured-document-blocks Specification

## Purpose
Define the parser contract for preserving trustworthy document structure, source ordering, and traceable text ranges across supported formats.

## Requirements
### Requirement: Ordered parsed block contract
The parser layer SHALL expose ordered structural blocks whose ranges trace to the containing section text and whose kind, structural path, parser revision, and `AUTHORITATIVE` or `HINT` confidence are explicit.

#### Scenario: Unknown parser element
- **WHEN** structured parser output contains an element outside the format allowlist
- **THEN** its text remains in source order without an invented authoritative block kind

#### Scenario: Repeated block text
- **WHEN** identical block text appears more than once
- **THEN** each block retains the range assigned while section text was assembled

### Requirement: DOCX structural allowlist
For DOCX input, the system SHALL preserve body order, paragraphs, standard Heading 1-6 hierarchy, and table/row/cell boundaries from structured Tika output while treating list numbering, nesting, and custom styles as best-effort metadata and never inferring code blocks.

#### Scenario: Heading and table document
- **WHEN** a DOCX fixture contains nested standard headings, paragraphs, and a table
- **THEN** parsed blocks retain their order, heading path, and table/row/cell boundaries as authoritative

#### Scenario: List and custom style
- **WHEN** a DOCX fixture contains numbered paragraphs or a custom paragraph style
- **THEN** source text is preserved and any classification is non-authoritative diagnostic metadata

### Requirement: Parser contract fixtures
The system SHALL pin accepted Tika 3.2.3 DOCX and PDF structural mappings with fixtures that cover order, text, range, kind, confidence, and structural path.

#### Scenario: Parser dependency changes accepted output
- **WHEN** a dependency upgrade changes an accepted fixture mapping
- **THEN** tests fail until the mapping is restored or a deliberate parser/chunker revision is approved
