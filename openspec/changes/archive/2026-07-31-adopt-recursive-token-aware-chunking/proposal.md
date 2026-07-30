## Why

The current fixed-character splitter can cut words, identifiers, sentences, and table rows, and it does not enforce the configured token budget. Once versioned token and parser-structure contracts exist, the ingestion baseline can move to deterministic recursive chunks with exact source provenance and retrieval-only contextualization.

## What Changes

- Make a LangChain4j-backed recursive strategy the default for new processing, wrapped by the project-owned chunking interface.
- Split within trustworthy parser structure using paragraph, line, English sentence, word, and character fallback tiers; non-English content skips the English sentence tier.
- Enforce target tokens, token overlap, and a hard character safety limit while tracking positions during construction rather than rematching returned text.
- Persist richer flat `CHILD` chunks with section order, source offsets, page range, structural path, block confidence, strategy revision, tokenizer identity, token count, and source hash.
- Keep exact `sourceText` separate from bounded, versioned `embeddingText`; embed contextual headers globally for supported formats but never expose or lexically index the synthetic prefix as source text.
- Preserve overwrite, cleanup, KB isolation, embedding-space, cancellation, and privacy-safe logging behavior.
- Keep the fixed strategy selectable for comparison and rollback; existing documents remain unchanged until explicitly reprocessed.

## Capabilities

### New Capabilities

- `recursive-document-chunking`: Recursive token-budget splitting, overlap, language fallback, exact offsets, deterministic identity, and enriched flat chunk persistence.
- `contextual-chunk-embeddings`: Versioned contextual embedding text that remains separate from citation and lexical source text.

### Modified Capabilities

- `runtime-application-settings`: Makes recursive chunking the effective default for subsequent processing and exposes validated live settings plus migration lifecycle metadata.
- `document-management`: Returns exact source text and richer provenance for persisted document chunks without presenting contextual headers as document content.
- `graph-data-plane-isolation`: Persists and queries richer child-chunk properties while retaining graph-only ownership and KB scope.
- `hybrid-search`: Embeds and retrieves contextualized child vectors while returning authoritative child source text and metadata.

## Impact

This affects chunk preparation, LangChain4j splitter integration, token counting, embedding input construction, Neo4j chunk persistence/indexes, document chunk APIs, hybrid search mapping, cleanup, runtime settings, and unit/integration/evaluation tests. Parent chunks, parent expansion, corpus migration orchestration, semantic chunking, and derived representations remain out of scope.
