## 1. Parsed Document Model

- [x] 1.1 Add `ParsedDocument` and `ParsedSection` models for document-level metadata and ordered section text.
- [x] 1.2 Adapt `DocumentParsingService` to return structured parsed output for processing while preserving a parse-to-string helper for schema generation.
- [x] 1.3 Update document processing orchestration to chunk parsed sections without crossing section boundaries.

## 2. Tika Option Mappings

- [x] 2.1 Add TXT, DOCX, and PDF/Tika option definitions to the processing option registry introduced by `add-document-processing-options`.
- [x] 2.2 Map PDF layout options such as sort by position, auto-space, duplicate text suppression, annotations, bookmarks, forms, and font-name extraction to Tika configuration.
- [x] 2.3 Map OCR options such as strategy, rendering strategy, DPI, image format, language, page segmentation mode, spacing, timeout, and skip OCR to Tika configuration.
- [x] 2.4 Map image and metadata options such as inline image handling, image metadata only, metadata inclusion, and write limits to Tika configuration.

## 3. Page-Aware PDF Parsing

- [x] 3.1 Implement PDF page section extraction using Apache Tika output that preserves page boundaries.
- [x] 3.2 Ensure `pdf.split-pages=false` keeps the current flattened parsing behavior.
- [x] 3.3 Ensure `pdf.split-pages=true` produces page sections with page number, page count, section index, parser id, and format metadata.
- [x] 3.4 Validate that OCR and layout options are applied per processing run from effective options.

## 4. Chunk Metadata

- [x] 4.1 Persist chunk metadata with source, parser id, format, processing run id, section index, and page details when available.
- [x] 4.2 Keep graph extraction input as chunk text only while preserving metadata for retrieval/search responses.
- [x] 4.3 Ensure page-aware chunks remain ordered by document order and chunk index.

## 5. Tests And Verification

- [x] 5.1 Add unit tests for Tika option mapping into `ParseContext`, `PDFParserConfig`, and OCR configuration.
- [x] 5.2 Add PDF fixture tests for page-aware parsing and default flattened parsing.
- [x] 5.3 Add document processing tests for page-aware chunk metadata and chunk ordering.
- [x] 5.4 Add regression tests showing TXT and DOCX processing remain unchanged by default.
- [x] 5.5 Run focused parser/document processing tests and `./mvnw test`.
