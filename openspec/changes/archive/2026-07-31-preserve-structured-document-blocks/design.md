## Context

`RoutedDocumentParser` already returns ordered sections and PDF pages, but the accepted PDF fixture proves only page order/text and the normal DOCX path is flattened. Recursive splitting needs evidence-backed structure rather than assumptions about arbitrary Tika XHTML.

This is proposal 2 of 6. It can be implemented independently, but its block model is consumed by `adopt-recursive-token-aware-chunking`.

## Goals / Non-Goals

**Goals:**

- Preserve an ordered, format-specific structural allowlist with source ranges.
- Make authoritative parser structure distinguishable from layout hints.
- Pin Tika 3.2.3 behavior with representative DOCX and PDF fixtures.
- Preserve compatibility for current section-text consumers.

**Non-Goals:**

- Infer semantic PDF headings, lists, tables, or code blocks.
- Treat DOCX list nesting or custom styles as authoritative.
- Change chunking, embedding, graph extraction, or retrieval behavior.

## Decisions

### Add ordered parsed blocks without removing sections

`ParsedSection` gains ordered `ParsedBlock` values containing block kind, exact text/source range, structural path, and `AUTHORITATIVE` or `HINT` confidence. The section text remains authoritative and blocks cover traceable ranges within it; existing consumers may continue reading only `section.text()`.

Alternative: replace sections with a generic document tree. Rejected as unnecessarily disruptive before chunking proves which structure is useful.

### Use a format-specific allowlist

For PDF, page boundary and text order are authoritative; paragraph/line boundaries are hints. For DOCX, body order, paragraphs, Heading 1-6 paths, and table/row/cell boundaries are authoritative. Lists and custom styles remain diagnostics, and code blocks are never inferred.

Unrecognized XHTML elements contribute ordered text but not invented block kinds.

### Route DOCX through structured XHTML

The Tika adapter uses the structured parse path for DOCX and maps only fixture-proven elements/styles. Source offsets are computed while assembling section text so repeated strings do not require reverse matching.

### Treat fixture changes as a revision decision

Pinned fixtures assert accepted block order, kind, confidence, path, and coverage. A dependency upgrade that changes accepted output must fail tests until maintainers either restore mapping or deliberately increment parser/chunker revision.

## Risks / Trade-offs

- [Tika XHTML differs across document producers] → Keep a narrow allowlist and preserve unknown content as plain ordered text.
- [Offsets drift during whitespace normalization] → Avoid destructive normalization and build section text and ranges together.
- [DOCX fixture coverage overstates general support] → Include multiple headings, paragraphs, tables, list markers, and custom styles; label best-effort fields.
- [Additional parser metadata increases memory] → Keep blocks lightweight and bounded to parser output.

## Migration Plan

1. Add backward-compatible parsed-block records.
2. Add DOCX and expanded PDF fixtures before switching DOCX routing.
3. Enable structured DOCX mapping and retain section-text fallback.
4. Roll back by disabling block consumption; persisted data is unaffected in this change.

## Open Questions

None.
