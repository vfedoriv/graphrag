# page-aware-tika-processing Specification

## Purpose
TBD - created by archiving change add-page-aware-tika-processing. Update Purpose after archive.
## Requirements
### Requirement: Tika processing options are applied per run
The system SHALL apply validated effective processing options to the Apache Tika parsing configuration used for the current processing run.

#### Scenario: PDF layout options are applied
- **WHEN** a PDF document is processed with valid effective PDF layout options
- **THEN** the system applies those options to the PDF parser configuration for that processing run
- **AND** later processing runs use their own effective options rather than stale parser state from earlier runs

#### Scenario: OCR options are applied
- **WHEN** a document is processed with valid effective OCR options
- **THEN** the system applies those options to the OCR parser configuration or parse context for that processing run
- **AND** OCR defaults are used for unspecified OCR options

#### Scenario: Metadata options are applied
- **WHEN** a document is processed with metadata extraction enabled
- **THEN** the parsed document includes normalized parser metadata that is safe to persist in processing artifacts

### Requirement: PDF parsing can preserve page boundaries
The system SHALL support page-aware PDF parsing that preserves page boundaries before chunking.

#### Scenario: PDF is processed with page splitting enabled
- **WHEN** a PDF document is processed with the effective option to split by page enabled
- **THEN** the parser returns ordered text sections corresponding to source pages
- **AND** each section records its page number and total page count when available

#### Scenario: PDF is processed with page splitting disabled
- **WHEN** a PDF document is processed without page splitting enabled
- **THEN** the parser returns text compatible with the existing flattened processing behavior
- **AND** chunks are not required to carry page numbers

### Requirement: Page-aware chunks preserve source page metadata
The system SHALL preserve page and parser metadata on chunks generated from page-aware parsed sections.

#### Scenario: Page section produces chunks
- **WHEN** a page-aware parsed section is split into chunks
- **THEN** every chunk from that section includes metadata for source filename, parser id, format, processing run id, section index, page number, and page count
- **AND** chunk indexes remain globally ordered across the document

#### Scenario: Page text exceeds chunk limits
- **WHEN** one PDF page contains more text than the configured chunk size
- **THEN** the page is split into multiple chunks using normal chunking limits
- **AND** each resulting chunk retains the same page metadata

#### Scenario: Consecutive pages are chunked
- **WHEN** page-aware processing chunks consecutive PDF pages
- **THEN** chunks do not cross page boundaries
- **AND** chunk ordering follows the original page order

### Requirement: Non-page-aware formats keep compatible behavior
The system SHALL preserve default TXT, DOCX, and non-page-aware PDF processing behavior unless applicable options change it.

#### Scenario: TXT document is processed
- **WHEN** a TXT document is processed without format-specific overrides
- **THEN** the parsed text and generated chunks remain compatible with existing TXT processing behavior

#### Scenario: DOCX document is processed
- **WHEN** a DOCX document is processed without format-specific overrides
- **THEN** the parsed text and generated chunks remain compatible with existing DOCX processing behavior

### Requirement: Classified PDF structure
The PDF parser SHALL treat page boundary and page text order as authoritative, expose paragraph and line boundaries only as layout-derived hints, and SHALL NOT recognize PDF headings, lists, tables, or code blocks without a future fixture-backed contract.

#### Scenario: Multi-page PDF
- **WHEN** Tika returns ordered page containers with layout-derived inner blocks
- **THEN** page number and order are authoritative while inner paragraph and line boundaries are marked as hints

#### Scenario: Ambiguous styled text
- **WHEN** PDF text visually resembles a heading, list, table, or code block
- **THEN** the parser preserves it as text without assigning one of those authoritative block kinds
