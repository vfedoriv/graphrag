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

The current structured-parser evidence is narrower than Tika's possible XHTML
output. The PDF fixture verifies ordered `div.page` extraction and page text
only. There is no DOCX parsing fixture, and the normal DOCX path currently uses
the flattened text returned by `ApacheTikaDocumentParser`, so it does not retain
XHTML block tags.

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
sentence splitter uses an English OpenNLP model. The first release supports
sentence-aware splitting for English only. For other languages, the recursive
strategy skips the sentence tier and falls back through preserved paragraph,
line, word, and character boundaries without claiming sentence awareness.

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

Use a format-specific structural allowlist:

- for PDF, treat page boundaries and text order as reliable; paragraph and line
  boundaries are layout-derived splitting hints, while headings, lists,
  tables, and code blocks remain untyped text unless a dedicated fixture proves
  otherwise;
- for DOCX, after switching the parser path to structured XHTML and adding
  fixtures, preserve body order, paragraph boundaries, standard Heading 1-6
  styles, and table/row/cell boundaries;
- treat DOCX list markers, nesting, and custom paragraph styles as best-effort
  diagnostics rather than authoritative block kinds because Tika emits list
  numbering inside paragraphs rather than semantic list elements;
- do not infer code blocks from font or layout. Recognize one only through a
  future explicit, fixture-tested style mapping.

PDF page boundaries remain hard boundaries for child/source spans and
citations, but are soft boundaries for parent context. A parent may combine
content from consecutive pages when the parser exposes compatible structural
continuity and the configured token and page-span bounds are satisfied. If
continuity is absent or ambiguous, parent construction stops at the page
boundary. DOCX and structured text parsing should retain headings and block
structure instead of flattening all content into one section.

Recognized DOCX tables should be chunked by logical row groups with headers
repeated in the embedding representation. Best-effort list paragraphs should
retain their introductory text when possible without inventing list hierarchy.
Very long identifiers, URLs, or unbroken strings use the final character
fallback.

### 3. Context-enriched embedding text

Enabled globally as part of the baseline for every embedded child, independent
of source format.

Keep two representations:

- `sourceText`: exact user document text used for citations and evidence;
- `embeddingText`: a bounded contextual representation used to generate the
  embedding.

An embedding representation prepends available stable context in this order:
document title or filename, format, heading/structural path, and page position.
Omit unavailable fields rather than emitting empty or guessed values.

```text
Document: Architecture Guide
Format: DOCX
Section: Persistence > Neo4j
Page: 4 of 12

<source chunk text>
```

Use the same header policy for TXT, PDF, and DOCX. The header is bounded,
versioned, and counted as part of the embedding input token and character
limits. Do not add format-specific enablement switches; format affects only
which trustworthy fields are available.

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

Persist each parent as its own `DocumentChunk` node with the complete bounded
parent text. This deliberately duplicates text covered by its children so
context expansion and graph-extraction retries do not need to reconstruct text
from overlapping child windows. Parent nodes are not embedded or included in
dense or lexical indexes in the first release. Children remain the precise
retrieval and text-retrieval citation units; graph-derived evidence cites its
extraction parent.

PDF parents may span consecutive pages, initially up to two pages for
evaluation, when the children remain separately page-bounded and share
compatible structural context. A cross-page parent carries an ordered page
range and child source spans; it is context for synthesis, never a replacement
for page-specific text-retrieval citation evidence. When used for graph
extraction, that bounded parent and its page range are the graph-fact citation.

Graph extraction should run on an independently selected bounded extraction
unit, normally the parent or another medium context chunk. Tiny retrieval
children may omit the context required to resolve entities and relationships.
Graph facts must link to the exact persisted extraction parent. The public
graph-fact result cites that parent only; it does not project or duplicate
child citations.

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
- required globally header-enriched `embeddingText` for embedded `CHILD`
  slices; absent for unembedded `PARENT` slices;
- exact token count under the selected estimator;
- document and section order;
- section-relative start and end offsets when reliable;
- child page number or parent start/end page and page count when known;
- heading path or structural path when known;
- parser block kind and confidence (`AUTHORITATIVE` or `HINT`) when structured
  XHTML supplied the boundary;
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
- `startPage`;
- `endPage`;
- `chunkerRevision`;
- `tokenizerId`;
- `sourceContentHash`.

Both parent and child nodes persist exact `sourceText` for their covered span.
Parents additionally persist their child count and a hash of the parent text.
Parent text must be materialized directly from tracked source spans during
chunk construction, not reconstructed by concatenating overlapping child
strings. A parent has no `embedding` or embedding-index label unless a later
separately proposed and evaluated change explicitly enables parent embeddings.

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

- dense retrieval embeds and searches `CHILD` chunks only, using globally
  header-enriched `embeddingText`;
- lexical indexes target `CHILD` source text, with structured metadata indexed
  or filtered separately;
- candidates remain keyed by the precise retrieval child ID;
- parent expansion becomes the primary coherent-context expansion and loads
  the persisted parent text after validating document, knowledge-base,
  strategy-revision, and parent-child scope;
- `chunkIndex +/- 1` remains a bounded fallback and must stay within compatible
  document, parent, and structural-section constraints; it may cross a page
  boundary only inside an accepted cross-page parent while the cited child
  remains page-bounded;
- graph evidence cites the persisted extraction parent, including its bounded
  text and page/structural range; public graph-fact results do not expose
  inferred child citations;
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
app.chunking.pdf-parent-cross-page-enabled=true
app.chunking.pdf-parent-max-pages=2
app.chunking.child-target-tokens=300
app.chunking.child-overlap-tokens=50
app.chunking.context-prefix-enabled=true
```

`app.chunking.context-prefix-enabled` is one global policy switch and defaults
to `true`; there are no per-format overrides. Changing it affects subsequent
processing only and changes the snapshotted chunker/embedding-representation
revision.

AI profiles also gain a typed `tokenizerId`. This is profile metadata rather
than a live chunking setting because it describes the embedding space and must
be snapshotted with the profile revision and processing run.

Final names and ranges belong in the runtime settings catalog and OpenSpec
change. Every setting must declare validation, mutability, live-apply behavior,
sensitivity, and lifecycle semantics.

Changing a chunking setting applies to subsequent document processing only.
The settings response should explicitly report that previously processed
documents retain their snapshotted strategy until reprocessed.

The token-counting policy must be explicit:

- auto-map only embedding model names whose tokenizer mapping is known:
  `text-embedding-ada-002`, `text-embedding-3-small`, and
  `text-embedding-3-large` use `cl100k_base`;
- allow an AI profile to declare a supported tokenizer explicitly for renamed
  or otherwise compatible models instead of inferring from the base URL;
- use the versioned `utf8-byte-v1` fallback for an unknown tokenizer, counting
  one estimated token per UTF-8 byte and reporting the estimate as
  conservative rather than exact;
- retain `max-characters` and provider request limits as safety guards;
- snapshot the estimator/tokenizer identity and revision;
- reject unknown explicit tokenizer identifiers rather than silently
  substituting another encoding;
- never assume `characters / 4` is an exact provider token count.

The fallback is an operational upper-bound policy for the supported
OpenAI-compatible integration, not a claim about every possible tokenizer. A
provider limit rejection remains retryable only through a bounded smaller-input
path; it must not trigger unbounded adaptive splitting.

## Chunk-Strategy Reprocessing Workflow

Generalize the existing durable knowledge-base reprocessing-plan resource
instead of introducing a separate chunk-migration orchestrator:

```http
POST /api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans
```

```json
{
  "reason": "CHUNK_STRATEGY_MIGRATION",
  "selection": {
    "mode": "OUTDATED_STRATEGY"
  },
  "target": {
    "expectedChunkerRevision": "recursive-v1"
  }
}
```

The typed selection modes are:

- `OUTDATED_STRATEGY`, the normal corpus-migration mode, selecting documents
  without chunks or whose persisted chunker/embedding-representation revision
  differs from the requested effective revision;
- `DOCUMENT_IDS`, for an explicit non-empty owned-document list;
- `ALL`, an explicit forced rebuild even when a document already matches.

Plan creation validates `expectedChunkerRevision` against the effective
configuration and returns `409 Conflict` for a stale target or any other active
destructive reprocessing plan for the same knowledge base, including a schema
reprocessing plan. It atomically snapshots:

- the complete typed chunk settings and their canonical hash;
- chunker, tokenizer/estimator, contextual-header, parser, and parent-hierarchy
  revisions;
- AI profile ID/revision and embedding-space identity;
- active schema ID/content hash used for graph extraction;
- effective document processing options;
- selected document IDs and source-content hashes.

The response is `202 Accepted` with the existing plan ID and status location.
Existing GET/list and linked retry resources expose per-item progress,
failures, stale sources, target currency, and retryability. Retry requires an
explicit unresolved-document resnapshot and creates a linked plan.

Workers invoke existing overwrite document processing with the immutable plan
snapshot, not whichever live settings happen to exist later. A document whose
binary hash changed becomes `STALE_SOURCE`. If the chunk settings/hash,
tokenizer, AI profile/embedding space, or active schema target changes, queued
items become `BLOCKED_TARGET_CHANGED`; the plan never mixes target revisions.
Items already completed remain independently committed and diagnosable.

Changing a runtime setting never creates this plan automatically. The settings
response reports the effective chunker revision and that older documents
require explicit reprocessing, allowing an operator to copy that revision into
the request as an optimistic target guard.

## Processing and Persistence Decisions

- Chunk preparation produces all required source, context, hierarchy, and
  provenance metadata before embedding.
- Embeddings are generated only for representations needed by enabled retrieval
  branches.
- Batch embedding stays bounded by both item count and total token/input size.
- Parent and child writes are atomic from the perspective of document
  replacement, or are made idempotent with recoverable checkpoints. A child
  must never reference a missing parent after a successful write.
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
3. Introduce `ChunkingStrategy`, `ChunkingContext`, `ChunkSlice`, the
   profile-owned `tokenizerId` contract, the known-model mapping and
   `utf8-byte-v1` fallback, and a compatibility adapter for the current fixed
   strategy.
4. Correct the token/character setting mismatch and persist chunker/tokenizer
   revision metadata.
5. Implement recursive token-aware splitting within existing `ParsedSection`
   boundaries, with deterministic structural fallbacks and source offsets.
6. Add representative DOCX heading/paragraph/table/list fixtures, route DOCX
   through structured XHTML, and preserve only the format-specific block
   allowlist; retain page-aware PDF behavior and keep non-page PDF structure as
   hints.
7. Enable the versioned global contextual-header policy for embedded children
   while retaining exact source text and excluding headers from lexical
   indexing and citations.
8. Update persistence, indexes, cleanup, APIs, and tests for the richer flat
   chunk model.
9. Implement parent nodes with persisted bounded text and ordered child
   materialization, including bounded structurally compatible cross-page PDF
   parents with page-bounded children, embedding only enabled retrieval units,
   and safe hierarchy traversal.
10. Update advanced search to retrieve children, expand parents, constrain
    adjacency, rerank bounded context, and cite exact source spans.
11. Generalize the existing PostgreSQL reprocessing plan/item workflow with the
    `CHUNK_STRATEGY_MIGRATION` target, explicit selection modes, immutable
    chunk/profile/schema snapshots, target-change blocking, progress, and
    linked retry.
12. Benchmark, select defaults, migrate the corpus, and only then retire the
    fixed-character strategy.
13. Defer parent embeddings and semantic or derived retrieval representations
    to separately proposed experiments after the deterministic rollout.
14. Run `graphify update .` after implementation.

## Testing and Evaluation

### Unit tests

- token limit and hard character limit enforcement;
- exact `cl100k_base` selection for known OpenAI embedding profiles, explicit
  tokenizer selection for compatible aliases, unknown-identifier rejection,
  and deterministic `utf8-byte-v1` fallback behavior;
- token overlap measured in tokens rather than characters;
- paragraph, line, sentence, word, and character fallbacks;
- no missing or duplicated source content outside configured overlap;
- stable ordering and deterministic identity;
- exact source offsets, including repeated text;
- English sentence-boundary behavior and deterministic paragraph/line/word
  fallback for documents whose language is not English;
- authoritative PDF page boundaries and hint-only PDF paragraph/line
  boundaries;
- DOCX body order, paragraphs, standard Heading 1-6 hierarchy, and
  table/row/cell boundaries, with list/custom-style best-effort behavior;
- no code-block inference without an explicit fixture-tested style;
- bounded cross-page PDF parent assembly, continuity rejection, and
  page-bounded child/source spans;
- parent-child containment and adjacency constraints;
- parent text materialized from source spans rather than overlapping child
  concatenation, and parent/child scope plus revision validation;
- globally enabled, stable-order contextual embedding headers for TXT, PDF, and
  DOCX; omission of unavailable fields; bounded header token accounting; and
  separation from lexical and citation text;
- typed settings validation and lifecycle reporting;
- reprocessing request selection/target validation, outdated-strategy
  selection, canonical snapshot hashing, duplicate-active-plan rejection, and
  target-change blocking;
- privacy-safe logging and diagnostics.

### Integration tests

- parser-to-chunk metadata propagation for TXT, DOCX, and PDF;
- Tika 3.2.3 structured-output contract fixtures that fail visibly when a
  dependency upgrade changes the accepted PDF or DOCX block mapping;
- page-aware PDF child chunks and citations, including retrieval through a
  bounded cross-page parent;
- parent/child Neo4j persistence and KB isolation;
- persisted parent text, absent parent embeddings/index labels, and
  atomic replacement/deletion of the complete hierarchy;
- vector and lexical indexes containing only intended chunk kinds;
- dense vectors built from global contextual `embeddingText` while lexical
  indexes contain unprefixed child `sourceText`;
- profile/embedding-space compatibility;
- graph evidence resolving to its authoritative extraction parent and public
  graph-fact results exposing that parent citation only;
- replacement and deletion cleanup of every chunk representation;
- reprocessing migration, retry, recovery, and partial failure;
- chunk-strategy plan execution using its immutable settings/profile/schema
  snapshot rather than later live values, including `STALE_SOURCE` and
  `BLOCKED_TARGET_CHANGED` outcomes;
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
- **Unsupported-language sentence errors.** Sentence-aware splitting is
  English-only in the first release. Documents in other languages skip the
  sentence tier and retain deterministic paragraph, line, word, and character
  fallbacks; additional language support requires separate fixtures and an
  explicit compatibility decision.
- **Lost source offsets after normalization.** Track offsets during splitting
  and avoid destructive normalization of authoritative source text.
- **Parser XHTML implies false structure.** Gate block kinds by format and
  fixtures. Persist whether a boundary is authoritative or a hint, and treat
  unrecognized/custom structures as text rather than guessing.
- **Higher storage and embedding cost.** Embed children by default; only embed
  children in the first release. Parent and derived embeddings require a
  separate evaluated change. Accept one bounded duplicated parent-text copy and
  measure its storage impact separately from vector storage.
- **Graph extraction loses context on small chunks.** Use parent or dedicated
  extraction units and preserve source-span mappings.
- **Mixed old and new chunk strategies during rollout.** Store strategy
  revisions, filter or diagnose mixed results, and use explicit corpus
  reprocessing.
- **A reprocessing plan silently changes target mid-run.** Snapshot the complete
  typed chunk/profile/embedding/schema target, use an expected revision guard,
  block queued work when any target component changes, and require an explicit
  linked retry with resnapshotting.
- **Parent expansion overwhelms the synthesis budget.** Apply per-parent,
  per-document, token, and evidence-count limits before reranking.
- **Parent and child text diverge.** Build both from the same tracked source
  spans, persist content hashes and strategy revisions, write the hierarchy
  atomically, and reject cross-scope or mixed-revision expansion.
- **Cross-page parents join unrelated content.** Require consecutive pages,
  compatible structural context, a small page-span limit, and page-bounded
  text-retrieval citation children; stop at the page boundary when continuity
  is ambiguous. Graph citations identify the accepted bounded parent page
  range.
- **Library behavior changes.** Hide splitter implementations behind a stable
  application interface and pin behavior with tests.
- **Generated representations hallucinate.** Treat them as derived retrieval
  hints only and require exact source-span provenance.

## Recommended Decision

Adopt recursive token-aware, structure-preserving chunks as the new baseline.
Enable contextual embedding headers globally for every embedded child. Then
add parent-child retrieval as the principal advanced-search context expansion
mechanism.

Do not begin with semantic breakpoint, proposition, hypothetical-question, or
late chunking. They add model cost and provenance complexity before the current
token-setting mismatch and arbitrary boundary behavior are corrected.

## Resolved Decisions

### Embedding-profile tokenizer policy

Store a typed `tokenizerId` on each AI profile and snapshot it with the profile
revision and processing run. Automatically select `cl100k_base` only for the
known OpenAI embedding models `text-embedding-ada-002`,
`text-embedding-3-small`, and `text-embedding-3-large`. Operators may explicitly
select a supported tokenizer for aliases or compatible OpenAI-style endpoints.

Do not silently treat an arbitrary OpenAI-compatible model as OpenAI-tokenized.
When neither an exact mapping nor an explicit supported tokenizer is available,
use the versioned `utf8-byte-v1` conservative estimator and preserve the hard
character and provider request guards. Persist whether the count is exact or
conservative alongside the tokenizer identity.

### Sentence-aware language scope

Support sentence-aware splitting for English only in the first release. When a
document is not known to be English, skip the sentence-boundary tier and split
using preserved paragraph, line, word, and final character boundaries. Do not
silently run the English sentence model on other languages.

Adding another sentence-aware language requires representative fixtures,
boundary-quality evaluation, and a versioned splitter-policy update.

### PDF page boundaries

Treat PDF page boundaries as flexible for parent context and absolute for
child/source spans and citations. A parent may span consecutive pages only when
structural continuity is available and both its token budget and page-span
limit are satisfied. Start evaluation with a maximum span of two pages; if the
parser cannot establish continuity, keep the parent within one page.

Persist the parent's start/end page and ordered child spans. Search and
synthesis may use the cross-page excerpt for context. Text-retrieval citations
continue to identify precise page-bounded child evidence, while graph-derived
facts cite the bounded extraction parent and its page range.

### Tika structured-block contract

Use a format-specific allowlist pinned by fixtures for Tika 3.2.3:

- PDF: page boundary and text order are authoritative. Paragraph and line
  boundaries are layout-derived hints. Headings, lists, tables, and code blocks
  are not recognized as structured PDF blocks in the first release.
- DOCX: preserve body order, paragraphs, standard Heading 1-6 styles, and
  table/row/cell boundaries after routing DOCX through structured XHTML.
  List numbering/nesting and custom styles remain best-effort metadata, not
  authoritative hierarchy. Do not recognize code blocks.

The current PDF fixture establishes only page separation and page text; the
repository has no DOCX parsing fixture yet. Add representative DOCX and richer
PDF contract fixtures before enabling the new block mapping. Parser upgrades
must rerun these fixtures and require a chunker/parser revision when accepted
output changes chunk boundaries.

### Parent text persistence

Persist every parent as its own `DocumentChunk` node with complete bounded
parent text. Materialize that text directly from tracked parser/source spans
during ingestion; do not rebuild it by concatenating overlapping child text.
Persist the parent kind, scope, structural/page range, strategy revision, token
count, child count, and content hash.

Parent nodes have no embedding and are excluded from dense and lexical indexes
in the first release. Advanced search retrieves children, follows
`parentChunkId`, and loads the stored parent text for bounded context after
validating scope and strategy revision. Children remain the precise public
text-retrieval citation units; graph evidence uses the extraction parent
citation defined below.

Document processing, replacement, deletion, retry, and cleanup treat the
parent plus all children as one hierarchy. A successful persisted hierarchy
cannot contain a child whose parent is missing. The accepted text duplication
must be measured, but avoiding reconstruction complexity and making expansion
and extraction retries deterministic take precedence in the first release.

### Public citation for graph evidence

Graph extraction evidence cites the persisted extraction parent only. Store the
parent ID as the evidence source chunk ID and expose that parent ID, bounded
source text or permitted excerpt, structural path, and page range in the public
graph-fact result. Do not infer or emit child citations for a fact extracted
from parent context.

Text retrievers continue to cite precise child chunks. A claim supported by a
graph fact uses the parent citation associated with that graph evidence;
text-derived claims use child citations. Citation validation must understand
both kinds and verify that the cited node belongs to the same knowledge base,
document, processing strategy revision, and retained extraction run.

### Contextual-header scope

Enable contextual embedding headers globally for every embedded child in TXT,
PDF, and DOCX documents. Use one versioned stable-order policy containing only
available document title/filename, format, heading/structural path, and page
position fields. Omit unavailable fields and do not provide per-format
enablement overrides.

The header is bounded and counts against embedding input limits.
`embeddingText` contains the header plus exact child source text, while
`sourceText` remains unmodified for lexical indexing, public evidence, and
citations. Parent nodes are unaffected because parent embeddings are disabled
in the first release. A global policy change applies only after document
reprocessing and increments the embedding-representation/chunker revision.

### Chunk-strategy reprocessing initiation

Use the existing
`POST /api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans` resource
with a typed `CHUNK_STRATEGY_MIGRATION` reason and target. Generalize the
current PostgreSQL plan/item workflow rather than creating parallel chunk-only
tables, workers, or endpoints.

`OUTDATED_STRATEGY` is the normal selection mode; explicit `DOCUMENT_IDS` and
forced `ALL` modes are also supported. The request carries the expected
effective chunker revision, while the server snapshots the full typed chunk
configuration, tokenizer/header/parser revisions, AI profile and embedding
space, active schema, processing options, and document hashes. Creation queues
the durable plan and returns `202 Accepted`.

Settings changes never start migration automatically. Workers process with the
immutable snapshot and block remaining items as `BLOCKED_TARGET_CHANGED` if
that target is no longer current. Existing status/list and linked retry
operations provide progress and explicit unresolved-document resnapshotting.

### Parent and derived retrieval embeddings

Do not enable parent embeddings, proposition embeddings, hypothetical-question
embeddings, summaries, or other derived retrieval vectors in the first
release. Retrieval must seed from contextualized child embeddings, lexical
child text, metadata, or graph evidence and may then expand to the stored
parent text.

The accepted drawback is that a parent cannot be discovered by dense similarity
unless at least one child becomes a candidate. Context spread across multiple
children may therefore be missed when no individual child ranks highly enough,
and the system cannot directly rank whole-parent topical coherence.

Mitigate this limitation with global contextual child headers, overlap,
lexical/graph retrieval, multiple child candidates, parent expansion, and
reranking over expanded context. Record missed-parent evaluation cases for a
future experiment.

Any future parent or derived embedding branch requires its own OpenSpec change
and must define acceptance thresholds for retrieval gain, regression,
latency, ingestion cost, vector storage, citation integrity, and KB isolation
before implementation or rollout.

## Open Questions

None.
