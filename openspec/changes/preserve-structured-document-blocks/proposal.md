## Why

Token-aware splitting cannot preserve meaningful boundaries that parsers have already flattened or never classified. The parser layer needs a small, fixture-backed structural contract before recursive chunking can safely rely on headings, paragraphs, tables, pages, or layout hints.

## What Changes

- Extend parsed-document output with ordered structural blocks, source ranges, structural paths, and authoritative-versus-hint confidence.
- Route DOCX parsing through Tika structured XHTML and preserve body order, paragraphs, standard Heading 1-6 hierarchy, and table/row/cell boundaries.
- Keep DOCX list markers, nesting, and custom styles as best-effort metadata; do not infer code blocks.
- Retain authoritative PDF page order and page boundaries while exposing paragraph and line boundaries only as layout-derived hints.
- Add pinned Tika 3.2.3 DOCX and richer PDF fixtures that fail visibly when accepted parser structure changes.
- Keep current flat section text available so existing processing remains compatible until recursive chunking is adopted.

## Capabilities

### New Capabilities

- `structured-document-blocks`: Format-specific parsed-block, source-range, hierarchy, confidence, and compatibility contracts.

### Modified Capabilities

- `page-aware-tika-processing`: Extends the PDF contract with explicit authoritative and hint-only boundary classifications without weakening page preservation.

## Impact

This affects `RoutedDocumentParser`, Tika parsing adapters, parsed-document domain records, parser metadata, PDF/DOCX fixtures, and parser-to-processing tests. It does not change persisted chunk shape, chunk sizes, embeddings, graph extraction, or retrieval behavior.
