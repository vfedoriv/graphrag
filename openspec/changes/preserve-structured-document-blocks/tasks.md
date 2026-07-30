## 1. Parsed Block Model

- [ ] 1.1 Add parsed block kind, confidence, structural path, source range, and parser revision value types.
- [ ] 1.2 Extend parsed sections with ordered blocks while preserving the existing section-text API and coverage invariants.
- [ ] 1.3 Add unit tests for repeated text, unknown elements, source-order preservation, and range validation.

## 2. Fixture-Backed Tika Mapping

- [ ] 2.1 Add representative DOCX fixtures covering Heading 1-6, paragraphs, tables, list markers, and custom styles.
- [ ] 2.2 Route DOCX through structured Tika XHTML and map only the accepted authoritative allowlist.
- [ ] 2.3 Expand PDF fixtures and map page/order as authoritative with paragraph/line boundaries as hints.
- [ ] 2.4 Preserve unrecognized XHTML text in order and prevent PDF/DOCX code-block or unsupported semantic inference.

## 3. Verification

- [ ] 3.1 Add parser-to-domain contract tests that pin Tika 3.2.3 block order, ranges, paths, kinds, and confidence.
- [ ] 3.2 Verify existing page-aware processing and flat section consumers remain compatible.
- [ ] 3.3 Run focused parser tests and the relevant PDF/DOCX Testcontainers processing integration tests.
- [ ] 3.4 Run `graphify update .` after implementation.
