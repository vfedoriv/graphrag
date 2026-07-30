## Context

The fixed strategy produces arbitrary character windows and cannot preserve exact offsets once text is stripped. The prior proposals define versioned tokenizer contracts and fixture-backed parser blocks. This change adopts the deterministic flat-child baseline before introducing parent hierarchy.

This is proposal 3 of 6 and requires `establish-versioned-chunking-contracts` and `preserve-structured-document-blocks`.

## Goals / Non-Goals

**Goals:**

- Make recursive token-aware splitting the default for newly processed documents.
- Preserve complete-unit overlap, exact source ranges, deterministic order, and revision identity.
- Separate authoritative source text from bounded contextual embedding input.
- Persist richer flat child chunks without weakening isolation or cleanup.

**Non-Goals:**

- Parent chunks, cross-page parent context, or graph extraction on parents.
- Parent-aware retrieval, semantic breakpoints, or generated representations.
- Automatic reprocessing or retirement of the fixed strategy.

## Decisions

### Use LangChain4j behind the project adapter

Use LangChain4j recursive splitter behavior as the main implementation primitive, with project code adapting parsed blocks, token estimation, offsets, language policy, and diagnostics. Paragraph, line, English sentence, word, and character tiers are attempted in order. For content not known to be English, the sentence tier is skipped.

LangChain4j `TextSegment` values are intermediate only. `ChunkSlice` remains authoritative and tests pin boundary behavior so dependency upgrades are deliberate.

### Track spans during packing

Structure units retain section-relative positions throughout recursive subdivision and packing. The implementation never locates a returned chunk with `indexOf`, which is ambiguous for repeated text. Overlap duplicates complete trailing units and records the duplicated source ranges.

### Keep page boundaries hard for flat children

Each child remains inside one parser section/page. PDF paragraph and line hints influence preferred boundaries but cannot override page scope. The hard character limit applies after contextual-header budgeting and provides a final bounded fallback for tokenizer-hostile strings.

### Separate `sourceText` and `embeddingText`

Persist exact child `sourceText`. Build `embeddingText` in stable field order from available filename/title, format, structural path, and page position, followed by source text. The header is versioned, bounded, and counted against token/character limits. Dense embedding uses `embeddingText`; public chunk reads, citations, and lexical indexing use `sourceText`.

### Persist queryable provenance fields

Fields used for ordering, filtering, cleanup, or later traversal become first-class Neo4j properties: kind, section and section-chunk indexes, offsets, pages, strategy/chunker revision, tokenizer ID, and source hash. Parser-specific diagnostics remain bounded metadata JSON.

Chunk IDs derive deterministically from document content revision, strategy revision, kind, section, and span, while overwrite remains authoritative.

## Risks / Trade-offs

- [LangChain4j upgrade changes boundaries] → Pin representative behavior and increment strategy revision on accepted change.
- [English sentence detection is applied incorrectly] → Require known English; otherwise skip the sentence tier.
- [Context headers consume useful source budget] → Bound headers, omit unavailable fields, and test final embedding input limits.
- [Mixed old/new chunks affect evaluation] → Expose revisions and defer migration to the dedicated reprocessing change.
- [Storage grows from richer properties] → Keep diagnostics bounded and measure bytes/chunks/embeddings per fixture.

## Migration Plan

1. Deploy new properties and readers before changing the default.
2. Add recursive strategy and evaluation tests alongside fixed compatibility mode.
3. Switch the effective default for subsequent processing only.
4. Verify retrieval uses contextual vectors but returns exact source text.
5. Roll back by selecting the fixed strategy; existing recursive chunks remain revision-identifiable.

## Open Questions

None.
