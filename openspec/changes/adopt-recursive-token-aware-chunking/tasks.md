## 1. Recursive Strategy

- [ ] 1.1 Add the LangChain4j recursive splitter integration behind the project-owned strategy adapter.
- [ ] 1.2 Implement position-aware structural unit packing and paragraph, line, English sentence, word, and character fallback behavior.
- [ ] 1.3 Implement token-measured complete-unit overlap, hard character fallback, and bounded diagnostics.
- [ ] 1.4 Add deterministic identity, exact offset, repeated-text, multilingual fallback, long-string, and page-boundary unit tests.

## 2. Representations and Persistence

- [ ] 2.1 Add stable, bounded contextual header construction and enforce token/character budgets over complete embedding input.
- [ ] 2.2 Separate child `sourceText` from `embeddingText` throughout preparation and embedding batching.
- [ ] 2.3 Extend `DocumentChunk` persistence with flat-child kind, order, offsets, pages, structure, strategy/tokenizer revision, token count, and source hash.
- [ ] 2.4 Update document chunk DTOs and graph adapters so public/lexical text remains authoritative source text.
- [ ] 2.5 Update dense embedding and hybrid-search mapping to use contextual vectors without returning synthetic headers.

## 3. Rollout and Verification

- [ ] 3.1 Add and validate recursive/context-header runtime settings and make recursive the default for subsequent processing only.
- [ ] 3.2 Update overwrite, cleanup, cancellation, privacy-safe diagnostics, and embedding-space tests for richer flat chunks.
- [ ] 3.3 Add TXT, DOCX, and PDF parser-to-chunk integration fixtures plus baseline-versus-recursive evaluation reporting.
- [ ] 3.4 Run focused unit tests and relevant Testcontainers processing, cleanup, profile, and hybrid-search integration tests.
- [ ] 3.5 Run `graphify update .` after implementation.
