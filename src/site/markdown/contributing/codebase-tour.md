# Contributor codebase tour

GraphRAG is a Java 25 / Spring Boot 4.1.0 REST API. Controllers are thin; business rules live in services; repository ports separate workflows from PostgreSQL and graph-only Neo4j adapters. Model-produced graph and query output is validated before persistence or execution.

## Package map

```text
src/main/java/io/github/vfedoriv/graphrag/
  ai/domain, application, ports  embedding rules, admission, and stored-state ports
  bootstrap/integration/ai, knowledgebase  document/assignment capability mapping
  bootstrap/integration/reprocessing  schema/document contract mapping
  config                   validated startup/runtime configuration
  controller               REST endpoints and OpenAPI annotations
  documents/contracts      public preparation, execution, outcome, and stored-state capabilities
  documents/application/inspection, lifecycle  embedding observations, counts, cleanup
  documents/api            document/chunking HTTP endpoints and API models
  documents/application/management  upload, cleanup, storage journals, chunking state
  documents/application/processing  stages, extraction, lifecycles, recovery, facades
  documents/domain         deterministic rules and internal values
  documents/ports          owned persistence, binary, graph, and model effects
  documents/adapters       relational, graph, binary, parsing, chunking, model effects
  bootstrap/DocumentsProcessingConfiguration  document stage assembly
  domain                   operational and graph domain types
  dto                      API request/response contracts
  embedding                embedding clients
  error                    RFC 7807 exception mapping
  graph                    schema-generation transformer support
  infrastructure           PostgreSQL, Neo4j, storage, and AI adapters
  knowledgebase/ports, contracts, application  lifecycle ports and assignment lookup
  llm / query              model contracts and Cypher client adapters
  observability            AI workflow/model observations and metrics
  repository               persistence ports
  schema                   schema JSON model/parser/validator
  schemas/reprocessing     consumer-owned ports and port-only item execution
  service                  lifecycle and orchestration services
  storage                  binary storage contract
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
`EmbeddingCompatibility` into `ai.domain` and the stored-observation port mapped
to `StoredEmbeddingsFacade`. For profile assignments, follow `ProfileAssignments`
through its adapter to `AiProfileAssignmentsFacade`. AI persistence owns profiles
only. The repository-free `EmbeddingSpacePolicy` bridge still serves the exact
search callers listed in the
[AI boundary details](../concepts/architecture.md#knowledge-base-lifecycle-and-ai-state-boundaries).
Document consolidation (step 4) is implemented. Registry/discovery consolidation
(step 5) remains pending; exact remaining dependencies are frozen through steps
5–9 in `ArchitectureBoundaryTest`.

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
