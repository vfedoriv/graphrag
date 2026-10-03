# Contributor codebase tour

GraphRAG is a Java 25 / Spring Boot 4.1.0 REST API. Controllers are thin; business rules live in services; repository ports separate workflows from PostgreSQL and graph-only Neo4j adapters. Model-produced graph and query output is validated before persistence or execution.

## Package map

```text
src/main/java/io/github/vfedoriv/graphrag/
  ai/contracts, domain, models, execution  immutable facts, identity, scoped execution
  ai/profiles              profile API, state, management, ports, relational adapters
  ai/adapters/provider     provider construction, resolver mechanics, revision caches
  bootstrap                configuration factories, startup loading, persistence scans
  bootstrap/integration    public-capability to consumer-port value mapping
  documents                owned API, workflows, rules, ports, and adapters
  documents/contracts      public source/preparation/execution/state/revision capabilities
  schemas/registry, discovery, generation  schema API, registry, analysis, model work
  schemas/drafts, evaluation, publication, reprocessing  durable schema workflows
  schemas/contracts        immutable schema snapshots and public capabilities
  knowledgebase            owned API, management, state, ports, relational adapters
  settings                 API, catalog, lifecycle, ports, relational adapters
  settings/contracts       typed immutable runtime access snapshots
  search/query, retrieval, ranking, answering, runs  query and durable search ownership
  indexes/contracts, domain, adapters/graph  shared vector/lexical maintenance
  observability            generic AI workflow/model observations
  logging                  metadata-only logging helpers
  http/contracts           common immutable pagination/request/error bases
  storage                  shared binary primitives
  persistence/transaction  store-qualified transaction annotations
```

## Guided tour

1. Start at `GraphragApplication.java`, `application.properties`, profile property files, `pom.xml`, and [architecture](../concepts/architecture.md).
2. Follow `KnowledgeBaseController` and `AiProfileController` into `KnowledgeBaseService`, `AiProfileService`, and `AiRuntimeModelFactory`.
3. Follow `SchemaController` into `SchemaRegistryService`, `SchemaParser`, `SchemaValidator`, generation/discovery services, and relational schema adapters.
4. Follow `DocumentController` into `DocumentUploadService`, `DocumentProcessingService`, processing stages, chunking strategies, extraction validation/write, and cross-store cleanup.
5. Follow `QueryController` through generation, validation, `EXPLAIN`, timeout-limited execution, and `QueryAskService`.
6. Follow `AdvancedSearchRunController` through readiness/admission, durable run service, processor branches, fusion/reranking/sufficiency/synthesis, and result publication.
7. Follow `SchemaDraftController` across lifecycle/source/analysis/review/evaluation/publication services and then `SchemaReprocessingPlanController`.
8. Read [testing](testing.md) and the canonical `EndToEndMvpFlowIntegrationTest` to see the full-flow contract.

For reprocessing, follow `SchemaReprocessingPlanService` into
`schemas.reprocessing.application.ReprocessingItemExecution` and its execution
port, then the bootstrap adapter and `DocumentReprocessingFacade`. Recovery uses
the outcome-reader port and `DocumentProcessingOutcomesFacade`. Schemas retains
claims/completion/retry policy; documents owns source checks, runtime migration
input restoration, profile scope, and processing-run inspection. Adapters only
map immutable contract values and introduce no encompassing transaction.

For preparation and currentness, follow the schemas-owned
`ReprocessingDocumentPreparation` port through its adapter to
`DocumentMigrationPreparationFacade`. Documents owns selection summaries,
parser/options resolution, chunk/run classification, and chunker/embedding target
inspection. Schemas keeps selection policy, schema checks, durable snapshot
assembly, preview aggregation, retry lineage, and plan persistence. Preview and
creation use the same read-only preparation; classification remains all-owned.
Architecture tests enforce this completed reprocessing boundary with no
preparation exceptions. AI compatibility uses AI-owned rules and stored-observation ports; document
workflows and adapters are consolidated under `documents`. See the
[architecture boundary details](../concepts/architecture.md#reprocessing-execution-and-recovery-boundary).

For knowledge-base deletion, follow `OwnedDocumentState` and
`KnowledgeBaseArtifactCleanup` through `KnowledgeBaseDocumentsAdapter` to
`KnowledgeBaseDocumentsFacade`. For profile compatibility, follow
`EmbeddingCompatibility` and `EmbeddingTarget` into `ai`; stored embedding
observations and profile assignments use their public ports and mapping adapters.
AI persistence owns profiles only. Search owns query/ask and advanced-search
APIs, workflows, policy, effects, and durable state in `search.query`,
`search.retrieval`, `search.ranking`, `search.answering`, and `search.runs`.
`SearchDocumentMetadataAdapter`, `SearchKnowledgeBaseAdapter`, and
`SearchSchemaAdapter` map public capabilities from `bootstrap.integration.search`;
document metadata selection is capped at 200 results and citation batches at 128
IDs. `EmbeddingSpacePolicy` has been removed and the exact step-8 boundary-test
exceptions and all step-nine support/assembly pairs are retired.
`ArchitectureBoundaryTest` and `FinalSupportBoundaryTest` enforce permanent rules.
Follow `RuntimeSettingsAccess`, AI profile/execution capabilities, and shared index
contracts for support consumers; bootstrap factories can wire implementations,
while integration adapters only map public contract values.

## High-risk invariants

- Schema `name + version` identity is immutable; active schemas cannot be updated or deleted.
- Existing chunks prevent incompatible provider/model/dimension/resolved-tokenizer profile changes.
- Document replace/delete and overwrite must clean only document-scoped derived artifacts and preserve facts supported elsewhere.
- Extraction stays within active-schema labels and relationship types.
- Query execution is read-only and validator-approved.
- Runtime settings are cataloged, typed, allowlisted, and lifecycle-aware.
- Application logs remain metadata-first and content-free.
- Do not use Java `var`; declare concrete types.

## Practical entry points

Lower-risk changes include DTO validation and controller tests, focused chunk/query validator tests, OpenAPI examples, and schema fixtures. Higher-risk changes include schema activation, cross-store cleanup, graph evidence/provenance, query validation, profile compatibility, durable run publication, and observability content handling.

Before editing behavior, check active and archived OpenSpec artifacts for prior product decisions. When a non-trivial contract changes, update the spec and the matching portal workflow in the same change.

## Review checklist

- Keep controllers thin and preserve `ProblemDetail` responses.
- Preserve repository-port boundaries and PostgreSQL/Neo4j ownership.
- Validate model output before graph writes/query execution.
- Add deterministic tests and the smallest relevant integration coverage.
- Keep `README.md`, `AGENTS.md`, and `CLAUDE.md` synchronized when shared contributor facts change.
- Run the commands in [testing](testing.md), including documentation alignment when portal/navigation/shared facts change.
