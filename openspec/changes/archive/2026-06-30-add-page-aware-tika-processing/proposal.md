## Why

Once processing options are typed and auditable, PDF/Tika parsing needs concrete behavior that improves retrieval quality: page-aware text extraction, safer layout/font/image/OCR controls, and chunk metadata that can cite source pages. This change implements those parser-specific capabilities on top of the processing option registry from `add-document-processing-options`.

## What Changes

- Add a Tika-backed parser adapter that produces structured parsed sections instead of one undifferentiated text string.
- Populate the registry with current-format TXT, DOCX, and rich PDF/Tika option mappings.
- Implement PDF page-aware parsing so chunks can preserve page numbers while still using normal chunk sizing and overlap rules.
- Map curated PDF/OCR/layout/font/image options to Apache Tika `ParseContext`, `PDFParserConfig`, `TesseractOCRConfig`, metadata inclusion, and content handlers.
- Persist page and parser metadata in chunk metadata for downstream search/citation.
- This change depends on `add-document-processing-options` and should be implemented after that proposal lands.

## Capabilities

### New Capabilities
- `page-aware-tika-processing`: Parser-specific Tika behavior, PDF page sectioning, option mappings, and chunk metadata for page-aware document processing.

### Modified Capabilities

## Impact

- Affected backend code: `DocumentParsingService`, `RoutedDocumentParser`, document processing orchestration, chunk metadata creation, and parser tests.
- Affected dependencies: existing LangChain4j Tika parser dependency may be used through configurable suppliers, or native Apache Tika classes already present transitively may be used directly.
- Affected retrieval metadata: document chunks gain parser id, processing run id, page number, page count, and section metadata when available.
- No change to upload deduplication, document replacement semantics, schema activation, graph extraction validation, or query read-only validation.
