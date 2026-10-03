# Feature modularization: decisions and migration roadmap

Date: 2026-10-03
Status: all nine roadmap steps are implemented, including final support ownership,
application assembly, and permanent feature-graph enforcement. All transitional
roadmap exceptions are retired.

## Purpose

Improve feature navigation and change isolation, make dependencies and testing
boundaries explicit, and retain a path toward reusable libraries and independently
deployable workers. These are complementary goals. Reuse and separate deployment
are options, not requirements for the initial migration.

This document records architectural decisions, current migration status, and
remaining roadmap choices. It is not an exhaustive package reference; the
architecture portal describes the current ownership boundaries. Detailed
OpenSpec changes govern each implementation slice.

## Findings from the current repository

- Repository ports, model-client interfaces, explicit processing stages, typed
  runtime settings, and architecture tests already provide useful boundaries.
- Feature implementations are consolidated under documents, schemas, search,
  knowledgebase, AI, and settings. Shared support and bootstrap have distinct,
  enforced roles rather than broad legacy implementation packages.
- `DocumentProcessingService` coordinates injected stages and handles application
  state, profile selection, and lifecycle checks; bootstrap owns stage assembly.
- Reprocessing preparation, execution, and recovery now use schemas-owned ports;
  documents owns preparation facts and processing inspection behind capabilities.
- Knowledge-base document counts/cleanup and AI stored embeddings/assignments now
  use owned ports and immutable capability mappings. AI owns deterministic rules;
  AI profile persistence no longer reads knowledge-base state.
- Search owns query/ask and advanced-search APIs, workflows, deterministic
  policy, retrieval effects, model adapters, and durable run persistence under
  `search.query`, `search.retrieval`, `search.ranking`, `search.answering`, and
  `search.runs`.
- Search consumes bounded document metadata and immutable knowledge-base/schema
  facts through public capabilities mapped by `bootstrap.integration.search`.
  AI owns embedding compatibility; the obsolete `EmbeddingSpacePolicy` bridge
  has been removed.
- Draft authoring now owns lifecycle, sources, durable analysis/recovery, review,
  conflicts, draft history, and its relational/binary persistence under
  `schemas.drafts`.
- Evaluation, publication, and reprocessing own their API/state, workflows,
  checkpoints, and persistence under their respective schema areas. Evaluation
  reads document preparation and per-chunk dry extraction through public
  capabilities; dry work writes no document processing state or graph artifacts.
- Evaluation/reprocessing owners supply immutable batch history/currentness facts.
  Draft navigation uses its own summary ports through mapping-only adapters,
  preserving bounded list queries and stable pagination.

## Confirmed decisions

1. Keep one application initially and organize it around features.
2. Combine feature ownership, narrow public contracts, architecture enforcement,
   and internal separation of rules, workflows, and adapters.
3. Keep document processing inside **documents**.
4. Keep registry, discovery, draft lifecycle, analysis, review, evaluation,
   publication, and reprocessing inside one **schemas** feature, with internal areas.
5. Keep query/ask and advanced search inside one **search** feature.
6. Features own the adapters implementing their operations. Documents owns chunk
   and extracted-artifact writes/cleanup; search owns retrieval/query adapters.
   Shared infrastructure supplies connections and transaction support rather
   than a broad graph module owning every operation.
7. Resolve circular feature interactions with consumer-owned ports and small
   integration adapters supplied by application assembly.
8. Establish boundaries before deciding on separate build modules or processes.

## Candidate organization

```text
graphrag/
  documents/
    api/
    contracts/
    application/management/
    application/processing/
    domain/
    ports/
    adapters/
  schemas/
    registry/
    discovery/
    drafts/
    evaluation/
    publication/
    reprocessing/
  search/
    query/
    retrieval/
    ranking/
    answering/
    runs/
  knowledgebase/
  ai/
  settings/
  observability/
  infrastructure/
  bootstrap/
```

Exact package names and the internal layouts of smaller areas are proposed
defaults. Do not create empty layers or an interface for every concrete helper.
Contracts are for meaningful consumer or external-effect boundaries.

Knowledge bases own lifecycle and profile/schema associations. AI owns profile
management, model resolution, provider adapters, and embedding compatibility
rules. Settings owns its catalog, validation, persistence, and typed access.
Observability supplies tracing/metrics. Bootstrap assembles the application and
integration adapters. Business prompts and result interpretation remain with the
feature using the models.

## Target source dependencies

The following arrows mean dependency on public contracts only, not repositories,
entities, or implementation classes:

```text
search       --> documents, schemas, knowledgebase, ai
documents    --> schemas, knowledgebase, ai
schemas      --> knowledgebase, ai
knowledgebase --> ai
```

Features can consume typed settings and observability support. Those support areas
must not depend on feature implementations. Bootstrap/integration code can depend
on both sides of an interaction to implement consumer-owned ports. Production
features must not depend on bootstrap.

For example, schemas owns a reprocessing document port. An integration adapter
implements it using the documents public capability. Schemas retains plan state,
claims, policy, and recovery orchestration; documents retains processing execution,
document/run state, and document-specific input preparation. The bridge translates
contracts and does not become another business workflow owner.

AI compatibility uses the same pattern where AI needs document-owned stored
embedding information. This avoids a source dependency from AI back to documents.
Runtime calls can flow in both directions without a source dependency cycle.

Cross-feature values should be immutable, purpose-specific contracts. Include
identity, ownership scope, source hash, schema hash, profile revision, tokenizer,
embedding-space identity, or effective configuration when the operation needs
them. Do not expose persistence entities, provider clients, or write-only keys.

## Migration roadmap

| # | Change | Outcome | Prerequisites |
|---|---|---|---|
| 1 | Isolate reprocessing execution and recovery | Schemas-owned execution/source-check/outcome ports and integration adapter; remove document repository access from execution/recovery paths | None |
| 2 | Move document migration preparation behind contracts | Documents owns selection data, options, classification, and document target preparation; schemas retains plan policy and state | 1 |
| 3 | Separate knowledge-base and AI state dependencies | Ports for document state, cleanup, and embedding information; deterministic compatibility rules | 2 |
| 4 | Consolidate documents | Management, processing, recovery, owned adapters, and deterministic logic are grouped coherently | 3 |
| 5 | Establish schema registry and discovery boundaries | Schema snapshots and revision-aware contracts; registry/discovery ownership and document input boundaries | 3 |
| 6 | Consolidate draft authoring | Lifecycle, sources, analysis, review, conflicts, and recovery remain schema-owned without foreign internal access | 4, 5 |
| 7 | Isolate evaluation and publication | Held-out preparation contracts, side-effect-free dry extraction, revision-specific readiness/publication, final reprocessing organization | 6 |
| 8 | Consolidate search | Implemented: query/ask and advanced search use public feature contracts and search-owned adapters; exact step-8 exceptions are retired | 4, 5 |
| 9 | Finalize support boundaries and assembly | Implemented: typed settings, AI/profile/tokenizer/model, KB, schema generation, indexes and common support have final owners; bootstrap assembly is distinct from mapping; zero roadmap exceptions | 4, 7, 8 |

Changes 4 and 5 can proceed independently after 3. Search need not wait for all
draft workflows. Each change must leave a working application and introduce its
own enforceable boundary; no repository-wide rename is required up front.

## Scope discipline

- Changes 1 and 2 wrap/move existing responsibilities without redesigning the
  processing algorithm, HTTP API, destructive-plan exclusion, or persisted format.
- Change 1 may retain named preparation dependencies for change 2. It must not
  claim that all schemas/document coupling has already disappeared.
- Change 6 established draft ownership without rewriting its analysis algorithms,
  API behavior, persistence history, or workflow semantics.
- Change 9 is limited to identified residual dependencies and application assembly.
- New architecture tests should reject new violations while freezing any remaining
  transitional exceptions by explicit class/path and removing them in later slices.
- Every slice includes focused behavior checks and aligned portal/contributor
  documentation where implementation facts change.

## Invariants and verification

Preserve HTTP paths, response/error contracts, ownership-safe selection, schema
identity/activation rules, held-out eligibility, profile compatibility, and typed
runtime configuration. Preserve persisted snapshots and historical readability.

PostgreSQL remains authoritative for operational state. Neo4j remains authoritative
only for graph-native artifacts. Do not introduce distributed transaction claims.
Preserve relational checkpoints, conditional claims, uniqueness, retry lineage,
idempotent external effects, and recovery after external success/checkpoint failure.
Moving lookups behind contracts must not weaken existing snapshot or transaction
guarantees. Logs remain metadata-only and provider secrets remain write-only.

Use existing deterministic tests as behavior anchors and add meaningful workflow
port/adapter tests and architecture rules. Run relevant persistence integration
coverage for snapshots, claim concurrency, and recovery; use escalated execution
for Docker/Testcontainers. During implementation, run documentation alignment and
site generation when the portal or shared contributor facts change.

## Deferred decisions

Separate build modules, extracted parsing/chunking/schema libraries, document
workers, and search workers require a concrete reuse or operational reason.
Remote execution would additionally need versioned transport contracts, binary
access, ownership, retries, cancellation, and deployment compatibility. Ordinary
in-process interfaces do not provide those guarantees by themselves.

Contract/package names in detailed changes are proposed implementation decisions.
Later proposals should be grounded in contracts actually established by changes
1 and 2 rather than pre-creating speculative interfaces for all nine changes.

## Detailed changes

- [Execution and recovery](../openspec/changes/archive/2026-10-01-isolate-reprocessing-execution-recovery/proposal.md)
- [Document migration preparation](../openspec/changes/archive/2026-10-01-isolate-document-migration-preparation/proposal.md)

- [Knowledge-base and AI state dependencies](../openspec/changes/archive/2026-10-01-separate-knowledge-base-ai-state-dependencies/proposal.md) (implemented and archived step 3)

- [Document consolidation](../openspec/changes/archive/2026-10-01-consolidate-documents/proposal.md) (implemented step 4)

## Step-5 registry and discovery boundary

Step 5 is implemented in
[the archived change](../openspec/changes/archive/2026-10-02-establish-schema-registry-discovery-boundaries/proposal.md).
`schemas.registry` owns definition state, parser/validator rules, and active
resolution. Knowledge-base association state is owned by `knowledgebase`, with
registry admission/association ports mapped to public capabilities by
`bootstrap.integration.schemas`. Reads and writes retain their caller transaction;
activation keeps the KB lock and schedules reprocessing after commit.

`schemas.contracts` provides immutable, complete schema snapshots containing
stored JSON and hash. Document extraction consumes snapshots for active and
expected targets. `schemas.discovery` owns source preparation, model analysis,
aggregation, and deadlines. Its document inputs are acquired through the
`DocumentSourceInputs` public capability, with scope checks, byte bounds before
file parsing, defensive copies, and no path or persistence-record exposure.

`ArchitectureBoundaryTest` rejects new foreign registry/discovery dependencies.
Search and support migration have retired their exact step-8 and step-9
exceptions. Draft
authoring's step-6 document and registry/discovery exceptions and the migrated
step-7 document, registry, and downstream persistence exceptions are retired.

## Step-6 draft authoring boundary

Step 6 consolidates draft authoring under `schemas.drafts`: API mapping,
lifecycle, source revisions, durable analysis and recovery, review decisions,
conflicts, navigation, draft-owned relational persistence, and binary storage
effects. The package has API, application, domain, ports, and adapter areas.
Existing SQL/table mappings, serialized snapshots, source history, binary
namespace, and HTTP contracts remain compatible; no SQL or binary migration is
required.

Draft workflows consume scoped document facts through the `DraftDocumentInputs`
port, mapped to documents' `DocumentSourceInputs` capability. Metadata and
fingerprint inspection, content reads, and parsing use immutable values without
document records or local paths. Schemas keeps source revision and hash checks,
stale/unavailable decisions, analysis bounds, and chunk/run policy. Loaded bytes
are checked against the captured source hash before parsing. Draft-owned
text/file content uses a schema-owned binary adapter over shared storage;
referenced document content remains document-owned.

Base schema identity, knowledge-base association, stored definition, and content
hash facts flow through `DraftSchemaLookup`, mapped to
`schemas.contracts.StoredSchemaSnapshots`. Metadata reads do not parse; review
inheritance requests a parsed snapshot separately. Managed knowledge-base admission,
active schema identity, and non-secret active AI profile ID/revision facts flow
through `DraftKnowledgeBases`, mapped to the knowledge-base-owned capability.
The integration adapters only translate immutable values and add no transaction
boundary; synchronous reads join the caller's relational transaction. Provider
client construction stays AI-owned.

Draft navigation consumes evaluation and reprocessing history/currentness through
bounded immutable summary ports. `ArchitectureBoundaryTest` and
`FinalSupportBoundaryTest` enforce final ownership; all step-6 through step-9
exceptions are retired.

## Step-7 evaluation, publication, and reprocessing boundary

Step 7 is implemented by
[the archived ownership change](../openspec/changes/archive/2026-10-02-isolate-schema-evaluation-publication/proposal.md).
`schemas.evaluation`, `schemas.publication`, and `schemas.reprocessing` own their
HTTP mapping, state, repository ports, relational adapters, checkpoints, and
recovery. Evaluation and reprocessing also produce their history and currentness
summaries; draft navigation consumes batch ports through transaction-free
`bootstrap.integration.schemas` adapters rather than downstream repositories.

Draft-owned contracts provide scoped admission, revision/projection, canonical
review decisions/guidance, contributor fingerprints, and publication linkage.
Registry-owned contracts provide stored immutable schemas, parsing/validation,
identity lookup, and inactive generated registration. Knowledge-base capabilities
provide managed admission, active schema, and non-secret profile identity and
embedding/tokenizer facts. Synchronous reads retain caller transactions; bridges
only map contract values and provider construction remains AI-owned.

Documents owns source loading, parsing, chunk splitting, model selection, and
validation behind evaluation preparation and per-chunk dry-extraction capabilities.
Evaluation retains its sequential outcome loop, eligibility, reuse keys, metrics,
and deterministic advisory fallback. Source checks precede reuse, client
availability, preparation, and extraction. Dry work creates no processing or
extraction runs, chunks/embeddings, or graph facts/relationships.

Publication preserves readiness/blocker ordering, exact revision/hash guards,
durable intent, resumable inactive registration, and completion with draft-owned
linkage in one relational transaction. Registry activation retains its lock and
after-commit trigger through `SchemaActivationReprocessing`. Reprocessing preserves
all-owned classification before selection, creation recomputation, destructive-plan
exclusion, authoritative item counters, and its historical recovery predicate.
HTTP contracts, SQL mappings, canonical snapshots/fingerprints, source-race
semantics, and external-work checkpoint separation remain compatible without SQL
or binary migration. Search and support consolidation (steps 8 and 9) are
implemented; no roadmap exceptions remain.

## Step-8 search ownership

Search consolidation is implemented across `search.query`, `search.retrieval`,
`search.ranking`, `search.answering`, and `search.runs`. These areas own their
HTTP/API values, workflows, deterministic policy, effect ports/adapters, and
durable run state. `search.runs.adapters.relational` owns the run, attempt, and
result entities and relational adapters; `search.retrieval.adapters.graph` owns
graph retrieval effects. Query execution and planner inspection use search-owned
adapters over the shared Neo4j driver.

Document metadata flows through the document-owned `DocumentMetadataAccess`
capability. Its citation batch is capped at 128 document IDs and metadata
selection at 200 results; `SearchDocumentMetadataAdapter` maps the immutable
facts to search ports. `SearchKnowledgeBaseAccess` supplies scoped admission and
non-secret active schema/profile identifiers. `SearchSchemaAdapter` maps
`StoredSchemaSnapshots`, `SchemaSnapshots`, and `CapturedSchemaParsing` to the
search-owned `SearchSchemas` port. Readiness checks schema availability without
parsing; run creation captures the exact stored definition and hash; workers parse
the captured content rather than substituting current registry state. Integration
adapters map public values and add no transactions.

AI owns embedding compatibility through `EmbeddingCompatibility` and immutable
`EmbeddingTarget`; `EmbeddingSpacePolicy` is removed. Graph-plan data and pure
validation are values/rules under `search.retrieval.domain` (`GraphPlanValidation`),
while `AdvancedSearchPlanValidator` and
`search.retrieval.application.validation.GraphPlanValidationService` own
application validation. Model-dependent planning, embedding, ranking, sufficiency,
and synthesis effects are placed in model adapters; search retains the prompts,
business policy, and interpretation needed by those workflows.

The exact step-8 schema, document-metadata, and compatibility exceptions are
retired along with every step-9 support/assembly pair. Document writes and cleanup
use shared index contracts and do not depend on search implementations.

## Step-9 final support and assembly boundary

Settings owns management, catalog/validation/lifecycle, and persistence. Consumers
use immutable `RuntimeSettingsAccess` snapshots. Search owns query-policy
composition; documents exposes supplied-snapshot chunk revision calculation,
mapped through a settings-owned port. The calculation does not re-read settings.

AI owns profile API/state/persistence, tokenizer and embedding identity, provider
construction/cache, and scoped model execution. Public profile facts contain no
credentials or mutable records; captured execution keeps selected model semantics
without exposing provider clients to workflows. Knowledge-base API/management is
consolidated, and schema generation prompts/normalization/model interpretation
are schema-owned. Shared vector/lexical contracts and graph adapters live in
`indexes`; search-specific metrics live in search. Metadata logging, binary
storage, common immutable HTTP bases, generic observations, and store-qualified
transaction annotations remain governed support.

Owned configuration records preserve existing property names/defaults. Bootstrap
assembles concrete implementations, loads schema resources, seeds the default
profile, and scans every owned persistence package. Integration adapters have
narrower privileges: public value mapping only, without clients, repositories,
transactions, or policy. The primary relational and named graph transaction
managers and transaction-aware Neo4j template keep their identities.

Permanent rules enforce the final source graph, foreign-state prohibition,
domain purity, provider confinement, and assembly/mapping distinction. The
[change inventory](../openspec/changes/archive/2026-10-03-finalize-support-boundaries-assembly/inventory.md)
accounts for all 179 frozen baseline pairs, including search's 74 outward and 14
inward pairs. Each has a final owner/capability and enforcing rule; no frozen
resource or broad legacy allowance survives. Independent existing transaction
self-calls are assessed by exact method signatures with unchanged participation.
Historical HTTP/JSON/SQL, snapshots/fingerprints, index identities, cache behavior,
metrics, and recovery/checkpoint contracts require no SQL or binary migration.
See the [canonical architecture](../src/site/markdown/concepts/architecture.md#final-support-and-assembly-ownership)
for current surfaces and the permitted source dependency graph.
