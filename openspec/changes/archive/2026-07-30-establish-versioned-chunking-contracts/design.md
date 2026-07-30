## Context

`ChunkingService` currently returns stripped fixed-width strings, interprets overlap tokens as characters, and estimates tokens as `ceil(characters / 4)`. `ChunkPreparationStage` cannot carry source positions or strategy identity, while AI profiles and processing runs do not snapshot a tokenizer policy. This change creates the contracts needed by later chunking proposals without changing production output.

This is proposal 1 of 6 and has no prerequisite change.

## Goals / Non-Goals

**Goals:**

- Define project-owned chunking, slice, token-estimator, and revision contracts.
- Make tokenizer selection explicit, deterministic, profile-aware, and persistable.
- Correct setting semantics without forcing an immediate production migration.
- Establish a reproducible fixed-character baseline and compatibility adapter.

**Non-Goals:**

- Adopt recursive splitting or structured parser blocks.
- Add parent-child chunks or retrieval expansion.
- Reprocess existing documents.
- Expose LangChain4j types outside the infrastructure adapter.

## Decisions

### Own the chunking boundary

Introduce `ChunkingStrategy.split(ParsedSection, ChunkingContext)` returning ordered `ChunkSlice` values. Slices carry source text, source positions, token count, strategy revision, tokenizer identity, and diagnostics. The existing fixed algorithm moves behind a compatibility strategy.

Alternative: make `ChunkingService` directly return LangChain4j `TextSegment` values. Rejected because library metadata, token estimators, and segment identity are not the application persistence contract.

### Resolve tokenizer policy from the AI profile

Add a typed optional `tokenizerId` to each profile revision. Known OpenAI embedding models map to `cl100k_base`; a supported explicit ID handles compatible aliases. Unknown models without an explicit mapping use versioned `utf8-byte-v1`, counted conservatively at one token per UTF-8 byte. Unknown explicit IDs are rejected.

Alternative: infer from the provider base URL or use characters divided by four. Rejected because neither is a stable tokenizer contract.

### Separate saved settings from effective revision

The runtime catalog exposes typed strategy, target-token, overlap-token, and hard character-limit values. A canonical hash over behavior-affecting settings plus strategy/tokenizer revisions forms the effective chunker revision. Live changes affect subsequent processing only and report that existing chunks remain on their snapshotted revision.

Compatibility aliases for current keys remain readable during this change; removal requires a separate breaking change.

### Snapshot provenance before behavior changes

Processing runs and new chunks record strategy, strategy revision, tokenizer/estimator ID, exact-versus-conservative count mode, and the canonical settings hash. The fixed strategy also records these fields, allowing a before/after evaluation with the same persistence shape.

### Pin evaluation behavior

Fixtures cover repeated text, long identifiers, multilingual UTF-8, paragraph/sentence boundaries, overlap accounting, and current fixed output. They measure correctness and costs but do not choose final recursive defaults.

## Risks / Trade-offs

- [Profile schema grows before recursive chunking uses it] → Keep fields backward compatible and seed null tokenizer IDs through deterministic model mapping.
- [Conservative byte counts create smaller chunks] → Report count mode and retain provider/hard-character guards.
- [Revision drift from non-canonical serialization] → Define ordered typed inputs and test canonical hashing.
- [Two setting names become confusing] → Document compatibility precedence and expose one effective value.

## Migration Plan

1. Add nullable profile and processing-run persistence fields.
2. Seed existing profiles through model mapping at read time without rewriting secrets.
3. Install the fixed compatibility strategy as the active default.
4. Record baseline fixtures and verify existing chunk output remains unchanged.
5. Roll back by retaining nullable metadata and selecting the fixed strategy.

## Open Questions

None.
