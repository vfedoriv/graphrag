# Feature modularization: decisions and migration roadmap

Date: 2026-10-01
Status: roadmap steps 1–4 implemented: reprocessing execution/recovery, document
migration preparation, knowledge-base/AI state boundaries, and document
consolidation. Step 5 (schema registry/discovery) remains pending.

## Purpose

Improve feature navigation and change isolation, make dependencies and testing
boundaries explicit, and retain a path toward reusable libraries and independently
deployable workers. These are complementary goals. Reuse and separate deployment
are options, not requirements for the initial migration.

This document records the architectural discussion and exploration. It is a
planning document, not a description of the repository's current package layout.
Detailed OpenSpec changes govern each implementation slice.

## Findings from the current repository

- Repository ports, model-client interfaces, explicit processing stages, typed
  runtime settings, and architecture tests already provide useful boundaries.
- Feature implementations are distributed across broad `service`, `domain`,
  `repository`, `controller`, and infrastructure packages, alongside narrower
  feature-oriented packages.
- `DocumentProcessingService` coordinates injected stages and handles application
  state, profile selection, and lifecycle checks; bootstrap owns stage assembly.
- Reprocessing preparation, execution, and recovery now use schemas-owned ports;
  documents owns preparation facts and processing inspection behind capabilities.
- Knowledge-base document counts/cleanup and AI stored embeddings/assignments now
  use owned ports and immutable capability mappings. AI owns deterministic rules;
  AI profile persistence no longer reads knowledge-base state.
- `EmbeddingSpacePolicy` delegates to AI compatibility only for frozen search
  callers (retirement in step 8); documents uses AI compatibility directly.
  Historical value/identity bridges and legacy `TokenizerId` support retire in
  steps 8/9.
- Search run management directly reads schema and knowledge-base repositories.
- Draft evaluation reads and prepares documents but must never persist its dry
  extraction to the knowledge-base graph.
- Draft analysis, review, evaluation, and publication have substantial workflows
  and shared durable state; migrating all schemas in one change would be too broad.

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
| 8 | Consolidate search | Query/ask and advanced search use public feature contracts and search-owned adapters | 4, 5 |
| 9 | Finalize support boundaries and assembly | Close explicit transitional exceptions; enforce final feature graph and support ownership | 4, 7, 8 |

Changes 4 and 5 can proceed independently after 3. Search need not wait for all
draft workflows. Each change must leave a working application and introduce its
own enforceable boundary; no repository-wide rename is required up front.

## Scope discipline

- Changes 1 and 2 wrap/move existing responsibilities without redesigning the
  processing algorithm, HTTP API, destructive-plan exclusion, or persisted format.
- Change 1 may retain named preparation dependencies for change 2. It must not
  claim that all schemas/document coupling has already disappeared.
- Change 6 establishes ownership, not a rewrite of every large draft service.
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

## Step-5 handoff from document consolidation

Document API, management/processing workflows, pure rules, ports, and owned
adapters now live under `documents`; processing assembly lives in
`bootstrap.DocumentsProcessingConfiguration`. Exact remaining edges and their
retirement steps 5–9 are recorded in `ArchitectureBoundaryTest`.

For the pending registry/discovery change, `DiscoverySourcePreparer` currently
uses `documents.application.management.DocumentUploadService`,
`documents.application.processing.DocumentParsingService`, `documents.domain.DocumentUploadNode`,
and `documents.ports.DocumentUploadRepository`. Map these owned inputs through
the planned `DocumentSourceInputs` capability rather than restoring legacy
package dependencies. `documents.application.processing.GraphExtractionService`
still uses `ActiveSchemaResolver`/`ActiveSchemaContext` and the mutable schema
record; replace this frozen step-5 edge with the proposed revision-aware
`SchemaSnapshots` capability. `documents.ports.DocumentGraphWriter` and
`GraphExtractionClient` carry `schema.SchemaDocument`; coordinate snapshot/model
mapping while preserving extraction target checks and schema JSON behavior.
`service.ChunkMigrationSnapshot` remains schema-owned. Draft/evaluation, search,
and support consumers keep their separately frozen steps 6–9; step 5 must not
broaden them or reopen completed reprocessing/knowledge-base/AI boundaries.
