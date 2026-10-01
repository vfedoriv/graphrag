# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Stack

- Java 25
- Spring Boot 4.1.0
- PostgreSQL 17 (all operational state, Flyway-managed `app` schema)
- Neo4j 5 (graph facts, provenance, chunks, and vector indexes only)
- Spring AI 2.0.0 (OpenAI-compatible) + LangChain4j 1.16.2
- OpenTelemetry + Micrometer AI observability, optional local Langfuse
- Maven Wrapper (`./mvnw`)

## Commands

```bash
# Build
./mvnw clean package

# Run (default profile, no AI provider configured)
./mvnw spring-boot:run

# Run with OpenAI profile
OPENAI_API_KEY=<key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=openai

# Run with OpenAI profile and local Langfuse tracing
OPENAI_API_KEY=<key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=openai,langfuse

# Run with LM Studio profile
LM_STUDIO_API_KEY=lm-studio ./mvnw spring-boot:run -Dspring-boot.run.profiles=lm_studio

# Tests
./mvnw test -Pfast
./mvnw test
./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest
./scripts/measure-test-suite.sh

# Documentation portal
./mvnw site
./mvnw site:run

# Required persistence services
docker compose up -d langfuse-postgres neo4j
docker compose exec -T langfuse-postgres bash /docker-entrypoint-initdb.d/20-init-graphrag.sh

# Neo4j + local Langfuse stack
docker compose --profile langfuse up -d

# Garage object-operation and persistence smoke test
./scripts/garage-smoke-test.sh
```

Use `./mvnw` instead of bare `mvn`.

## Architecture

- API prefix: `/api/v1`
- Error format: RFC 7807 `ProblemDetail`
- Layering: Controllers -> Services -> repository ports -> PostgreSQL adapters or graph-only Neo4j adapters

Reprocessing preparation, execution, and recovery use schemas-owned ports under
`schemas.reprocessing.ports`, mapped by `bootstrap.integration.reprocessing` to
public document capabilities. `DocumentMigrationPreparationFacade` owns source
selection summaries, option/parser resolution, chunk/run classification, and
chunker/embedding target inspection. Schemas retains selection policy, schema
checks, durable snapshot assembly, plan claims, completion, and retry policy.
Preview and creation share read-only preparation; creation recomputes facts.
`ArchitectureBoundaryTest` rejects document implementation dependencies across
all reprocessing paths, with no preparation exceptions. Preserve the existing
recovery predicate, all-owned classification scope, snapshot formats, and
separation of relational checkpoints from external processing. Synchronous
preparation reads participate in the caller's transactions; integration adapters
only map immutable values and add no transactions. AI compatibility uses AI-owned
rules and stored-observation ports; full feature relocation remains deferred.
See [architecture](src/site/markdown/concepts/architecture.md#reprocessing-execution-and-recovery-boundary).

Knowledge-base deletion reads document counts and requests scoped cleanup through
`knowledgebase.ports`, mapped under `bootstrap.integration.knowledgebase` to
`KnowledgeBaseDocumentsFacade`. AI owns deterministic embedding identity/tokenizer
compatibility under `ai.domain` and admission through `EmbeddingCompatibility`;
`StoredEmbeddingsFacade` supplies raw observations through `ai.ports` and
`bootstrap.integration.ai`. Profile assignment presence/IDs come from
`AiProfileAssignmentsFacade`; AI profile persistence reads only profiles.
Synchronous count/assignment reads join caller transactions. Cleanup failure
prevents relational deletion but may leave earlier external effects.
`EmbeddingSpacePolicy` remains a delegating bridge for processing, migration
preparation, and search; its exact callers are frozen by architecture tests.

Main controllers:
- `SchemaController` (create/list/get/update/delete/validate/activate, schema generation, review-only multi-source discovery, example generation, KB schema listing)
- `KnowledgeBaseController`
- `DocumentController` (upload/list/replace/delete/process/chunks)
- `QueryController`
- `AdvancedSearchRunController` (readiness, submit/list/poll/result/cancel durable advanced-search runs)

Key services:
- `SchemaRegistryService` (schema parse/validate/versioning + guarded inactive-schema update/delete)
- `SchemaDraftEvaluationService` (held-out dry extraction + durable deterministic evaluation results)
- `SchemaDraftPublicationService` (revision-specific readiness + inactive schema publication)
- `SchemaReprocessingPlanService` (durable bounded post-activation overwrite orchestration)
- `DocumentUploadService` (multipart upload + SHA-256 dedup + replace/delete artifact cleanup)
- `DocumentProcessingService` (parse -> chunk -> embed -> graph extract -> persist)
- `GraphExtractionService` (schema-constrained extraction + validation)
- `CypherGenerationService`
- `CypherValidationService` (blocked keywords + schema checks + `EXPLAIN` + auto `LIMIT`)
- `CypherExecutionService`
- `AdvancedSearchRunService` (durable run admission, ownership, polling, cancellation, retention, and result publication)
- `DefaultAdvancedSearchRunProcessor` (planned multi-branch retrieval, fusion, expansion, reranking, cited answering, and partial-result handling)
- `SchemaBootstrapService` (loads bootstrap schemas on startup)
- `AiObservationService` (AI workflow spans, model call metrics, privacy-controlled content metadata)
- `RuntimeSettingsService` (allowlisted runtime setting overrides, restart lifecycle metadata, live logging control + typed live accessors)
- `SchemaDiscoveryService` (bounded owned-document/text/file analysis, active-profile source calls, deterministic conflict-aware aggregation, review-only projection)
- `AiProfileService` (OpenAI-compatible AI profile CRUD, write-only API keys, default profile seeding)
- `AiRuntimeModelFactory` (profile/revision-scoped Spring AI OpenAI chat and embedding clients)

Application logging is metadata-first and separate from AI observation content capture. Normal logs may include identifiers, lengths, counts, timings, statuses, exception classes, and non-reversible fingerprints, but must not include document text, prompts, queries, model responses, generated schemas, or extracted graph payloads. Use `AiObservationService` capture settings for controlled trace content; do not reintroduce previews through `INFO` or `DEBUG` logs.

## Profiles

- `default`: no AI provider auto-config
- `openai`: requires `OPENAI_API_KEY`
- `lm_studio`: requires `LM_STUDIO_API_KEY=lm-studio`
- `langfuse`: enables AI observability and exports OTLP traces to local Langfuse defaults

At startup the PostgreSQL-backed default AI profile is seeded from `app.model.*` when no default exists. New knowledge bases receive the default profile. Document processing, extraction, Cypher generation, `/ask`, advanced search, and KB-scoped schema generation resolve the active knowledge-base AI profile at runtime. Profile API keys are write-only and must not be returned by read APIs. Profiles may declare the supported explicit `cl100k_base` tokenizer; known OpenAI embedding models resolve to it automatically and unknown models use versioned conservative `utf8-byte-v1` counting.

Runtime setting overrides are persisted in PostgreSQL. `mutable=true` means editable through the settings API; `liveApplied`, `updateMode`, `activeValue`, and `lifecycleState` describe whether the saved value applies immediately or after restart. Live mutable overrides cover query, advanced search, chunking, extraction, AI observability, and `logging.level.root` via Spring Boot logging. Startup idempotently migrates exact legacy hybrid equivalents (`max-candidates` and the default evidence-text flag), keeps explicit advanced overrides authoritative, and retires every legacy hybrid key. Canonical chunking keys are `strategy`, `target-tokens`, `overlap-tokens`, and `hard-character-limit`; `max-tokens` and `max-characters` remain compatibility aliases with canonical-key precedence. Chunking updates validate atomically and apply only to subsequent attempts. Processing runs and chunks snapshot strategy, settings, tokenizer/count mode, and effective chunker revisions. Supported non-secret restart-required settings such as `app.storage.documents-root` may be persisted as desired values and reported as `pending-restart` until the backend restarts with that value active. The list API also exposes read-only, restart-required, profile-managed, and sensitive-read-only entries for relevant `application.properties` groups such as application identity, Spring AI bootstrap/OpenAI aliases, Spring auto-configuration, PostgreSQL datasource/schema/pool metadata, Neo4j, storage, multipart, actuator/health, tracing, and OpenTelemetry exporter settings. Profile-resolved startup properties are reported as defaults. Settings consumed before PostgreSQL-backed overrides can load remain deployment-managed unless a safe runtime reassignment path exists; PostgreSQL and Neo4j connectivity, credentials, database/schema selection, and pool metadata stay deployment-managed through environment variables, Docker Compose, or equivalent configuration. Use AI profile APIs for provider behavior changes instead of raw `app.model.*` or `spring.ai.openai.*` edits. API keys, datasource/Neo4j passwords, and OTLP authorization headers must remain masked in read responses.

## Key Files

- `src/main/resources/application.properties`
- `src/main/resources/application-openai.properties`
- `src/main/resources/application-lm_studio.properties`
- `src/main/resources/application-langfuse.properties`
- `src/main/resources/schemas/*.json`
- `compose.yaml`

## Documentation Portal

The canonical detailed documentation is the Markdown portal under
`src/site/markdown`, with navigation in `src/site/site.xml`. Build it with
`./mvnw site` into `target/site` and preview it with `./mvnw site:run`; no Node
or Python toolchain is required. Current branch-specific repository links use
`main`: the backend portal is
`https://github.com/vfedoriv/graphrag/blob/main/src/site/markdown/index.md`, while
frontend controls and screenshots belong to
`https://github.com/vfedoriv/graphrag-ui/tree/main`.

When implementation behavior, public workflows, configuration defaults, or
shared contributor facts change, update the matching portal page and keep
`README.md`, `AGENTS.md`, and `CLAUDE.md` synchronized. Add every portal page to
`src/site/site.xml` and run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest`
plus `./mvnw site`.

## Testing

- `./mvnw test -Pfast` runs deterministic non-container tests; `./mvnw test` remains the complete credential-free suite
- Application integration tests share one JVM-scoped PostgreSQL and Neo4j container, reset state before each test, and execute sequentially
- Fresh-server provisioning and startup tests use independent PostgreSQL containers
- `./scripts/measure-test-suite.sh` preserves timing, inventory, context, container-start, and slowest-test reports under `target/test-performance`
- AI clients are mocked for deterministic tests
- Canonical full-flow integration test: `EndToEndMvpFlowIntegrationTest`

## Persistence Operations

PostgreSQL is authoritative for profiles, settings, knowledge bases, schemas,
documents, runs, draft workflows, publications, and reprocessing. Neo4j contains
only chunks/embeddings, schema-defined facts, evidence, provenance, direct scope,
and graph-native relationships. The local Langfuse deployment shares the PostgreSQL
server through a separate `langfuse` database and role. Its event and media
objects use pinned Garage `v2.3.0` with separate persistent metadata and data
volumes, an idempotent initializer, an internal event endpoint, and a
host-reachable media endpoint. GraphRAG document and schema-draft binaries
remain on the configured local filesystem.

The `langfuse-postgres` service is ignored for Spring Boot service-connection
discovery, so explicit
`GRAPHRAG_POSTGRES_*` settings route GraphRAG to `graphrag / graphrag / app`.
Never run `docker compose down -v`, delete the `langfuse_postgres_data` volume, or
drop the `langfuse` database. Preserve both `langfuse_garage_meta` and
`langfuse_garage_data`. Back up both Garage volumes consistently and
regularly verify that the pair can be restored.

## Docker and Testcontainers

Docker and Testcontainers cannot run inside the Codex sandbox.

Always request escalated execution immediately for:

- `docker` and `docker compose` commands
- Maven test/build commands that execute Testcontainers
- Integration and end-to-end tests requiring Neo4j containers

Place Maven arguments after the goal so project execution rules match, for
example: `./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest`.

## OpenSpec Workflow

OpenSpec artifacts are the source of historical product decisions. Archived changes under `openspec/changes/archive` document completed implementation work. For new behavior or contract changes, add or update OpenSpec specs before implementation when the change is non-trivial.

When changing shared contributor guidance in `README.md`, `AGENTS.md`, or `CLAUDE.md`, keep overlapping implementation facts and the canonical portal synchronized in the same change.

## Commit & Pull Request Guidelines

Use short imperative commit messages such as `add schema activation panel`. Keep commits focused and reviewable. Do not mention in commit messages "openspec" unless the user explicitly asks about it.

## Design Constraints

- Schema identity is immutable (`name + version` cannot change after save)
- Inactive schema content can be replaced only when the schema identity (`name + version`) is unchanged; active schemas cannot be updated or deleted
- Document replacement/deletion must clean document-scoped chunks, extraction runs, graph relationships, obsolete extracted nodes, and local binary content
- Extraction must stay constrained to active schema labels/relationship types
- Query execution is read-only and validated before run
- Do not use the Java `var` keyword; declare the concrete variable type explicitly.
- Runtime setting overrides must stay allowlisted, validated, and typed; do not add ad hoc stringly-typed setting reads in feature services. Catalog additions must declare editability, update mode, live-apply status, sensitivity, lifecycle behavior, and rejection or restart reason.
- Knowledge-base AI profile changes must reject embedding provider/model/dimension or resolved-tokenizer incompatibility once chunks exist and must leave the previous active profile unchanged on failure.
