# Improve Document Chunking

## Summary

Replace the current fixed-character document splitting with a versioned,
structure-preserving, token-aware chunking pipeline. Deliver the change in two
steps:

1. Make recursive token-aware chunks the default ingestion unit while
   preserving page, section, heading, and source-span provenance.
2. Add parent-child retrieval so advanced search can retrieve precise child
   chunks and expand them into coherent parent context before reranking and
   answer synthesis.

Semantic, proposition, hypothetical-question, late-chunking, and other
model-assisted representations remain optional experiments until the
deterministic baseline is measured.

The preferred steady-state model is:

```text
Parsed document
    |
    +-- page/section/heading boundaries
            |
            +-- parent context chunks (medium, coherent)
                    |
                    +-- child retrieval chunks (small, precise)
                            |
                            +-- dense embedding
                            +-- lexical indexing

Graph extraction uses a bounded context unit and every derived fact remains
linked to an authoritative source span.
```

## Current State and Findings

`RoutedDocumentParser` returns ordered `ParsedSection` values and can preserve
PDF page boundaries. `ChunkPreparationStage` processes each section
independently, so page-aware PDF chunks do not currently cross page boundaries.
Chunk metadata includes the source filename, parser, format, processing run,
section index, and page information when available.

Within each section, `ChunkingService` currently:

- strips the complete section text;
- creates fixed-width substrings using `app.chunking.max-characters`;
- applies `app.chunking.overlap-tokens` as a character count;
- does not use `app.chunking.max-tokens`;
- estimates token count as `ceil(characters / 4)`.

Consequences:

- chunk boundaries can split words, sentences, identifiers, table rows, and
  other semantic units;
- the configured 80-token overlap is effectively an 80-character overlap;
- the configured 800-token maximum is not enforced;
- token estimates and actual provider limits can diverge, especially across
  languages and embedding models;
- arbitrary boundary cuts weaken exact phrase and identifier matching for the
  lexical retriever planned in `ADVANCED_SEARCH_PLAN.md`;
- the flat `DocumentChunk` model has a global `chunkIndex` but no explicit
  parent, source offsets, heading path, chunk kind, or chunker revision.

The advanced-search plan already proposes adjacent-chunk expansion using
`chunkIndex +/- 1`. That is a useful fallback, but it cannot distinguish a
coherent parent context from an accidentally adjacent chunk and may cross
section or page boundaries unless metadata is checked.

Changing chunk settings currently affects only later processing. Existing
documents are intentionally not reprocessed automatically, so adopting a new
strategy requires an explicit bounded reprocessing operation.

## Goals

- Enforce chunk size using an explicit token-counting policy.
- Prefer structural and natural-language boundaries over arbitrary offsets.
- Preserve exact, bounded source provenance for citations.
- Improve dense and lexical retrieval without weakening KB or embedding-space
  isolation.
- Support parent-child retrieval without introducing a second persistence or
  orchestration framework.
- Keep graph facts traceable through evidence to authoritative source chunks.
- Make chunking deterministic, versioned, observable, and evaluable.
- Preserve current overwrite, cleanup, cancellation, and reprocessing
  semantics.
- Keep runtime settings allowlisted, validated, typed, and lifecycle-aware.

## Non-Goals

- Introduce an open-ended LLM ingestion agent.
- Replace PostgreSQL or Neo4j ownership boundaries.
- Use generated summaries or propositions as authoritative citation text.
- Adopt provider-specific late chunking as the initial implementation.
- Run automatic corpus-wide reprocessing after a live setting change.
- Use LangChain4j's Neo4j parent-child ingestor directly.
- Optimize chunk sizes from intuition alone without evaluation fixtures.

## Candidate Techniques

### 1. Token-aware recursive chunking

Recommended as the new baseline.

Split within an existing parser section in this order:

```text
paragraph -> line -> sentence -> word -> character fallback
```

Pack the largest complete units that fit the target token budget. Apply overlap
using complete sentences or smaller complete units rather than duplicated raw
character windows. Keep `max-characters` as a hard safety limit for malformed
or tokenizer-hostile input, not as the primary target.

LangChain4j 1.16.2 already provides
`DocumentSplitters.recursive(maxTokens, overlapTokens, tokenCountEstimator)`.
It preserves document metadata and assigns segment indexes. Its built-in
sentence splitter uses an English OpenNLP model, so it must not silently become
the only sentence-boundary policy for multilingual corpora.

Spring AI 2.0.0 provides `TokenTextSplitter`, with token limits, punctuation
breakpoints, minimum chunk lengths, and a configurable JTokkit encoding. It is
useful as a tokenizer-aware primitive but does not provide the same recursive
overlap behavior.

Whichever library implementation is selected must be wrapped behind a
project-owned interface. Provider/model tokenization, multilingual boundaries,
source offsets, settings, and provenance are application contracts rather than
library details.

References:

- [LangChain4j RAG and recursive splitting](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/rag.md)
- [Spring AI 2.0 TokenTextSplitter](https://docs.spring.io/spring-ai/docs/2.0.0/api/org/springframework/ai/transformer/splitter/TokenTextSplitter.html)

### 2. Structure-aware chunking

Recommended as part of the baseline, not as a separate optional mode.

Preserve or derive, where supported:

- document title;
- heading path;
- page and section;
- paragraph, list, table, and code-block boundaries;
- section-relative and, when reliable, document-relative character offsets;
- source format and parser revision.

PDF page boundaries remain hard citation boundaries unless a future evaluated
strategy explicitly permits cross-page parents. DOCX and structured text
parsing should retain headings and block structure instead of flattening all
content into one section.

Tables should be chunked by logical row groups with headers repeated in the
embedding representation. Lists should retain their introductory text when
possible. Very long identifiers, URLs, or unbroken strings use the final
character fallback.

### 3. Context-enriched embedding text

Recommended as a low-cost enhancement.

Keep two representations:

- `sourceText`: exact user document text used for citations and evidence;
- `embeddingText`: a bounded contextual representation used to generate the
  embedding.

An embedding representation can prepend stable context:

```text
Document: Architecture Guide
Section: Persistence > Neo4j

<source chunk text>
```

Do not overwrite or expose the contextual prefix as if it appeared verbatim in
the source. Lexical indexing should normally use `sourceText` plus separately
indexed structured metadata, avoiding artificial term-frequency boosts from
repeated prefixes.

### 4. Parent-child, or small-to-big, chunking

Recommended as the second implementation phase.

Starting ranges for evaluation, not final defaults:

- parent context: approximately 700-1,200 tokens;
- child retrieval chunk: approximately 200-350 tokens;
- child overlap: approximately 10-20 percent, bounded to complete units.

Dense and lexical retrieval operate on children. Fusion and reranking retain
the child hit and its channel diagnostics. Context expansion resolves the
parent and may add bounded adjacent children from the same parent or structural
section. The final evidence model keeps the precise child/source span while
providing a bounded parent excerpt for synthesis.

Graph extraction should run on an independently selected bounded extraction
unit, normally the parent or another medium context chunk. Tiny retrieval
children may omit the context required to resolve entities and relationships.
Graph facts must link to source spans that advanced search can resolve into
citations.

LangChain4j includes a Neo4j `ParentChildGraphIngestor`, but it creates its own
labels, index, dimension defaults, and retrieval query. Using it directly would
bypass this project's deterministic KB labels, embedding-space policy,
provenance model, cleanup rules, and custom persistence adapters. Reuse the
parent-child pattern inside the existing architecture instead.

### 5. Semantic breakpoint chunking

Deferred experiment.

Embed sentences or small blocks and split where adjacent semantic similarity
drops significantly. This can help narrative documents whose formatting does
not reflect topic boundaries.

Costs and risks:

- additional embedding work during ingestion;
- threshold and embedding-model sensitivity;
- less deterministic chunk identities after model changes;
- more complex multilingual evaluation;
- harder exact source-offset preservation.

If evaluated, snapshot the embedding space, algorithm revision, threshold, and
normalization policy with the processing run.

### 6. Proposition and hypothetical-question representations

Deferred auxiliary representations.

An LLM can derive atomic propositions, normalized facts, summaries, or
hypothetical questions and embed them as additional retrieval entries. These
may improve question-to-evidence matching and complement graph extraction.

Generated text must:

- be clearly marked as derived;
- link to one or more exact authoritative source spans;
- never be returned as a verbatim citation;
- be excluded when its source document or processing run is replaced or
  deleted;
- use the active KB AI profile with snapshotted model and prompt revisions;
- treat source content as untrusted prompt data.

### 7. Late chunking and multi-vector representations

Deferred research.

True late chunking generally requires a long-context embedding model that
exposes token-level representations so chunk vectors can be pooled after the
full document is encoded. The current generic OpenAI-compatible embedding
abstraction returns final vectors and does not expose this contract.

Multi-vector retrieval can still be implemented without true late chunking by
embedding children, parents, summaries, or propositions separately, but it
increases storage, index management, fusion branches, cleanup work, and
evaluation complexity.

## Proposed Domain Model

Introduce a project-owned chunking boundary:

```text
ChunkingStrategy
    split(ParsedSection, ChunkingContext) -> List<ChunkSlice>
```

`ChunkingContext` should contain typed effective settings and any model/profile
information needed to resolve the token-counting policy.

`ChunkSlice` should carry at least:

- `sourceText`;
- optional `embeddingText`, or enough context to build it later;
- exact token count under the selected estimator;
- document and section order;
- section-relative start and end offsets when reliable;
- page number and page count when known;
- heading path or structural path when known;
- `chunkKind`, initially `PARENT` or `CHILD`;
- stable parent reference for children;
- strategy name and revision;
- tokenizer/estimator identity;
- structural-boundary and fallback diagnostics.

Do not rely on matching returned chunk strings back into the source after
splitting. Repeated text makes that ambiguous. The splitter should track source
positions while constructing chunks.

Extend persisted graph data with explicit, queryable fields where needed:

- `chunkKind`;
- `parentChunkId`;
- `sectionIndex`;
- `sectionChunkIndex`;
- `startOffset`;
- `endOffset`;
- `chunkerRevision`;
- `tokenizerId`;
- `sourceContentHash`.

Continue storing bounded parser-specific details in metadata JSON. Fields used
for filtering, ordering, joins, cleanup, or traversal should be first-class
properties rather than hidden in JSON.

Use deterministic chunk identity derived from document content revision,
strategy revision, chunk kind, section, and source span where practical.
Processing overwrite still remains the authoritative replacement operation;
deterministic IDs improve reproducibility and diagnostics but must not weaken
cleanup or ownership checks.

## Integration with Advanced Search

Update `ADVANCED_SEARCH_PLAN.md` when parent-child behavior is accepted:

- dense retrieval embeds and searches `CHILD` chunks only;
- lexical indexes target `CHILD` source text, with structured metadata indexed
  or filtered separately;
- candidates remain keyed by the precise retrieval child ID;
- parent expansion becomes the primary coherent-context expansion;
- `chunkIndex +/- 1` remains a bounded fallback and must stay within compatible
  document, parent, section, and page constraints;
- graph evidence can originate from an extraction/parent chunk but must resolve
  to precise source spans and child citations;
- reranking receives bounded contextual excerpts without losing child-level
  provenance;
- final evidence reports retrieval chunk identity separately from expanded
  context identity;
- diagnostics record the chunk strategy/revision and whether parent or adjacent
  expansion contributed;
- diversity limits continue to count by document, not by parent.

The lexical-index backfill described in the advanced-search plan works for
existing flat chunks, but it does not convert them to the new chunk strategy.
Chunk-strategy migration requires document reprocessing and a deliberate
compatibility decision during rollout.

## Configuration

Replace ambiguous settings with typed, explicit concepts. Candidate keys:

```properties
app.chunking.strategy=recursive
app.chunking.target-tokens=800
app.chunking.overlap-tokens=80
app.chunking.max-characters=4000
app.chunking.parent-target-tokens=1000
app.chunking.child-target-tokens=300
app.chunking.child-overlap-tokens=50
app.chunking.context-prefix-enabled=true
```

Final names and ranges belong in the runtime settings catalog and OpenSpec
change. Every setting must declare validation, mutability, live-apply behavior,
sensitivity, and lifecycle semantics.

Changing a chunking setting applies to subsequent document processing only.
The settings response should explicitly report that previously processed
documents retain their snapshotted strategy until reprocessed.

The token-counting policy must be explicit:

- use an estimator compatible with the selected embedding model when available;
- otherwise use a documented conservative estimator;
- retain `max-characters` and provider request limits as safety guards;
- snapshot the estimator/tokenizer identity and revision;
- never assume `characters / 4` is an exact provider token count.

## Processing and Persistence Decisions

- Chunk preparation produces all required source, context, hierarchy, and
  provenance metadata before embedding.
- Embeddings are generated only for representations needed by enabled retrieval
  branches.
- Batch embedding stays bounded by both item count and total token/input size.
- Parent and child writes are atomic from the perspective of document
  replacement, or are made idempotent with recoverable checkpoints.
- Document replacement/deletion removes parents, children, embeddings, derived
  retrieval representations, graph evidence, relationships, obsolete facts,
  and local binary content under current cleanup rules.
- Knowledge-base AI profile changes continue rejecting incompatible embedding
  models or dimensions once embedded chunks exist.
- Normal application logs remain content-free. Record identifiers, strategy
  revision, counts, lengths, boundary types, fallback counts, timings, and
  fingerprints only.
- Controlled trace content continues through `AiObservationService` settings.

## Implementation Sequence

1. Create an OpenSpec change for advanced chunking and update affected runtime
   settings, document processing, graph data plane, cleanup, reprocessing, and
   advanced-search delta specs.
2. Add evaluation fixtures and capture the current fixed-character baseline
   before changing production behavior.
3. Introduce `ChunkingStrategy`, `ChunkingContext`, `ChunkSlice`, tokenizer
   policy, and a compatibility adapter for the current fixed strategy.
4. Correct the token/character setting mismatch and persist chunker/tokenizer
   revision metadata.
5. Implement recursive token-aware splitting within existing `ParsedSection`
   boundaries, with deterministic structural fallbacks and source offsets.
6. Extend parsing and metadata for heading/block structure where formats support
   it; retain page-aware PDF behavior.
7. Add contextual embedding text while retaining exact source text.
8. Update persistence, indexes, cleanup, APIs, and tests for the richer flat
   chunk model.
9. Implement parent and child materialization, embedding only enabled retrieval
   units, and safe hierarchy traversal.
10. Update advanced search to retrieve children, expand parents, constrain
    adjacency, rerank bounded context, and cite exact source spans.
11. Add bounded explicit reprocessing for existing documents and expose
    progress, failures, retry, and strategy snapshots.
12. Benchmark, select defaults, migrate the corpus, and only then retire the
    fixed-character strategy.
13. Evaluate semantic or derived representations separately after the
    deterministic rollout.
14. Run `graphify update .` after implementation.

## Testing and Evaluation

### Unit tests

- token limit and hard character limit enforcement;
- token overlap measured in tokens rather than characters;
- paragraph, line, sentence, word, and character fallbacks;
- no missing or duplicated source content outside configured overlap;
- stable ordering and deterministic identity;
- exact source offsets, including repeated text;
- multilingual sentence behavior;
- page, section, heading, table, list, and code-block boundaries;
- parent-child containment and adjacency constraints;
- contextual embedding text separated from citation text;
- typed settings validation and lifecycle reporting;
- privacy-safe logging and diagnostics.

### Integration tests

- parser-to-chunk metadata propagation for TXT, DOCX, and PDF;
- page-aware PDF chunks and citations;
- parent/child Neo4j persistence and KB isolation;
- vector and lexical indexes containing only intended chunk kinds;
- profile/embedding-space compatibility;
- graph evidence resolving to authoritative source spans;
- replacement and deletion cleanup of every chunk representation;
- reprocessing migration, retry, recovery, and partial failure;
- advanced-search retrieval, parent expansion, reranking, and citation
  referential integrity.

### Evaluation matrix

Compare at least:

1. current fixed-character baseline;
2. recursive token-aware chunks;
3. recursive plus contextual embedding text;
4. parent-child retrieval;
5. any later semantic or derived representation experiment.

Use the advanced-search fixture set and report:

- Recall@10 and Recall@20;
- MRR or nDCG for ranked evidence;
- exact identifier and phrase recall;
- semantic paraphrase recall;
- multi-document comparison coverage;
- graph-fact retrieval and citation resolution;
- answer claim support and citation integrity;
- chunks and embeddings per document;
- ingestion latency, model calls, tokens, and storage;
- search latency and expanded-context size;
- duplicate/near-duplicate evidence rate;
- results by language, format, document length, and question class.

Do not select defaults solely from the aggregate score. Preserve the
advanced-search target of improving mixed lexical/semantic/graph Recall@10
without materially regressing semantic-only questions, and require 100% KB
isolation and citation referential integrity.

## Risks and Mitigations

- **Tokenizer mismatch across AI profiles.** Use a profile-aware tokenizer
  policy, snapshot its identity, and keep conservative safety limits.
- **Multilingual sentence errors.** Do not depend exclusively on the built-in
  English sentence model; evaluate language-aware alternatives and preserve
  paragraph/line fallbacks.
- **Lost source offsets after normalization.** Track offsets during splitting
  and avoid destructive normalization of authoritative source text.
- **Higher storage and embedding cost.** Embed children by default; only embed
  parents or derived representations when an evaluated retrieval branch needs
  them.
- **Graph extraction loses context on small chunks.** Use parent or dedicated
  extraction units and preserve source-span mappings.
- **Mixed old and new chunk strategies during rollout.** Store strategy
  revisions, filter or diagnose mixed results, and use explicit corpus
  reprocessing.
- **Parent expansion overwhelms the synthesis budget.** Apply per-parent,
  per-document, token, and evidence-count limits before reranking.
- **Library behavior changes.** Hide splitter implementations behind a stable
  application interface and pin behavior with tests.
- **Generated representations hallucinate.** Treat them as derived retrieval
  hints only and require exact source-span provenance.

## Recommended Decision

Adopt recursive token-aware, structure-preserving chunks as the new baseline.
Add contextual embedding headers where evaluation confirms a gain. Then add
parent-child retrieval as the principal advanced-search context expansion
mechanism.

Do not begin with semantic breakpoint, proposition, hypothetical-question, or
late chunking. They add model cost and provenance complexity before the current
token-setting mismatch and arbitrary boundary behavior are corrected.

## Open Questions

- Which embedding-profile tokenizers can be supported exactly, and what
  conservative fallback should be used for unknown OpenAI-compatible models?
- Which languages must sentence-aware splitting support in the first release?
- Should PDF parents ever span pages, or should page boundaries remain absolute?
- What structured blocks can Tika reliably preserve for current PDF and DOCX
  fixtures?
- Should parent text be stored as its own node or reconstructed from ordered
  source children?
- Should graph evidence cite the extraction parent, the precise child spans, or
  both in the public result?
- Are contextual headers enabled globally or selected by document format?
- What explicit API/workflow should initiate corpus reprocessing after a
  chunk-strategy change?
- What evaluation thresholds justify enabling parent embeddings or any derived
  retrieval representation?
