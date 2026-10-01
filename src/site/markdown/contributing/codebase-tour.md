# Contributor codebase tour

GraphRAG is a Java 25 / Spring Boot 4.1.0 REST API. Controllers are thin; business rules live in services; repository ports separate workflows from PostgreSQL and graph-only Neo4j adapters. Model-produced graph and query output is validated before persistence or execution.

## Package map

```text
src/main/java/io/github/vfedoriv/graphrag/
  application/processing   staged document-processing pipeline
  bootstrap/integration/reprocessing  schema/document contract mapping
  config                   validated startup/runtime configuration
  controller               REST endpoints and OpenAPI annotations
  document                 parsing/chunking and structural source model
  documents/contracts      public reprocessing execution and outcome capabilities
  documents/application/processing  transitional facades over legacy processing
  domain                   operational and graph domain types
  dto                      API request/response contracts
  embedding                embedding clients
  error                    RFC 7807 exception mapping
  graph                    extraction validation/write/cleanup
  infrastructure           PostgreSQL, Neo4j, storage, and AI adapters
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

Document selection, classification, option resolution, and snapshot preparation
remain in the legacy plan service. Architecture tests freeze its exact document
repository/record, option resolver, and chunking dependencies for the next
preparation-isolation change. The new contracts and facades represent the first
modularization slice; the rest of the broad packages remain in place. See the
[architecture boundary details](../concepts/architecture.md#reprocessing-execution-and-recovery-boundary).

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
