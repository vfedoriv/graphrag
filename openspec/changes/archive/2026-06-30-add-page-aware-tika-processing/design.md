## Context

The current parser boundary returns a single `String`. LangChain4j's `ApacheTikaDocumentParser` can include metadata and accepts suppliers for `Parser`, `ContentHandler`, `Metadata`, and `ParseContext`, but the repository currently uses its zero-argument defaults through `RoutedDocumentParser`.

Apache Tika exposes parser-specific configuration through `ParseContext` and configuration objects such as `PDFParserConfig` and `TesseractOCRConfig`. PDF extraction also emits XHTML page wrappers, which can be used to split parsed content by page before normal chunking.

This change assumes `add-document-processing-options` has introduced the registry, effective options, and processing run model.

## Goals / Non-Goals

**Goals:**

- Produce structured parsed sections with page metadata when PDF page splitting is enabled.
- Preserve existing behavior for TXT, DOCX, and PDF when no relevant options are supplied.
- Map a curated, typed set of PDF/Tika/OCR options to Apache Tika configuration.
- Store page-aware chunk metadata for retrieval and citations.
- Keep page-aware processing as one uploaded document and one processing run.

**Non-Goals:**

- Materialize PDF pages as separate uploaded documents.
- Force one chunk per page regardless of chunking limits.
- Extract, store, or serve embedded images as first-class document artifacts.
- Expose arbitrary Tika native configuration keys.
- Add image-file upload support beyond PDF image/OCR handling.

## Decisions

1. Introduce a structured parsed document result.

   Parsing returns a `ParsedDocument` containing document-level metadata and ordered `ParsedSection` entries. Each section has text plus metadata such as page number, page count, parser id, format, and section index.

   Rationale: page-aware chunk metadata cannot be recovered reliably from a flattened string. The alternative was inserting sentinel strings into text, but that is brittle and contaminates embeddings and graph extraction.

2. Keep page-aware mode as page-bounded chunking, not page-as-chunk.

   When `pdf.split-pages=true`, each page section is passed through normal `ChunkingService` splitting. Chunks do not cross page boundaries, but large pages can produce multiple chunks and short pages can produce one chunk.

   Rationale: this preserves page citations without creating oversized chunks. The alternative "one chunk per page" ignores existing chunk size and token budget settings.

3. Use curated Tika option mappings.

   The registry maps safe option keys to `PDFParserConfig`, `TesseractOCRConfig`, metadata inclusion, content handler limits, and parse context setup. Unknown native Tika keys remain rejected.

   Rationale: Tika has broad and version-sensitive configuration. A curated mapping gives stable validation and safer defaults.

4. Prefer native Tika section extraction where page metadata is required.

   The implementation can keep LangChain4j parser usage for simple text extraction, but page-aware PDF mode should use a Tika `ContentHandler` or XHTML parsing path that preserves `<div class="page">` boundaries.

   Rationale: the current LangChain4j `DocumentParser` abstraction returns only one document text and metadata map, so it is insufficient by itself for page sections.

5. Store parser/page metadata on chunks.

   Chunk metadata JSON includes at least `source`, `parserId`, `format`, `processingRunId`, `sectionIndex`, and, when known, `pageNumber` and `pageCount`.

   Rationale: hybrid search already returns chunk metadata; preserving page data there gives clients citation context without changing graph schema extraction.

## Risks / Trade-offs

- [Risk] XHTML page boundary parsing may be sensitive to Tika output changes. -> Mitigation: keep tests around representative PDF fixtures and isolate parsing into a focused adapter.
- [Risk] OCR and image extraction options can be slow or resource-heavy. -> Mitigation: expose conservative defaults, validate ranges such as DPI/timeouts, and document option costs in descriptions.
- [Risk] Page-bounded chunking may reduce cross-page context. -> Mitigation: make page splitting an option and keep default behavior compatible until users opt in.
- [Risk] Font/image metadata can grow large. -> Mitigation: keep chunk metadata bounded and store detailed Tika document metadata only where explicitly enabled and normalized.

## Migration Plan

1. Add `ParsedDocument` and `ParsedSection` models behind the existing parsing service.
2. Keep current parse-to-string behavior for schema generation and any legacy callers by joining parsed sections when needed.
3. Add Tika PDF adapter tests for default parsing, page splitting, metadata inclusion, and selected PDF/OCR option mappings.
4. Update document processing to chunk sections and write page-aware metadata.
5. Run focused document processing tests and full Maven tests.

Rollback is to disable page-aware mode and return to the existing flattened parser path while leaving the processing options/run-history contract intact.

## Open Questions

- Should page-aware mode become the default for PDFs after validation with real user documents, or remain opt-in until retrieval quality is measured?
