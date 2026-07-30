# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tech Stack

- **Language:** Java 25, **Framework:** Spring Boot 4.1.0
- **Operational database:** PostgreSQL 17 (Spring Data JPA + Flyway `app` schema)
- **Graph database:** Neo4j 5 (facts, provenance, chunks, and vector indexes only)
- **LLM Integration:** Spring AI 2.0.0 (OpenAI-compatible) + LangChain4j 1.16.2
- **AI Observability:** OpenTelemetry + Micrometer, optional local Langfuse
- **Document Parsing:** LangChain4j Apache Tika
- **Build:** Maven (use `./mvnw`, never bare `mvn`)
- **API Docs:** SpringDoc OpenAPI at `/swagger-ui/index.html`

## Commands

```bash
# Build
./mvnw clean package

# Run (no AI model configured)
./mvnw spring-boot:run

# Run with OpenAI
OPENAI_API_KEY=<key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=openai

# Run with OpenAI and local Langfuse tracing
OPENAI_API_KEY=<key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=openai,langfuse

# Run with local LM Studio
LM_STUDIO_API_KEY=lm-studio ./mvnw spring-boot:run -Dspring-boot.run.profiles=lm_studio

# Fast deterministic tests (no Docker)
./mvnw test -Pfast

# Complete suite (shared Testcontainers PostgreSQL and Neo4j)
./mvnw test

# Single test class
./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest

# Preserve a full-suite performance report
./scripts/measure-test-suite.sh

# Start required persistence services
docker compose up -d langfuse-postgres neo4j
docker compose exec -T langfuse-postgres bash /docker-entrypoint-initdb.d/20-init-graphrag.sh

# Start Neo4j + local Langfuse stack
docker compose --profile langfuse up -d

# Garage object-operation and persistence smoke test
./scripts/garage-smoke-test.sh
```

Neo4j default credentials (dev): `neo4j / notverysecret`, ports `7474` (HTTP) and `7687` (Bolt).

## Architecture

### Layers

```
REST Controllers → Services → repository ports → PostgreSQL adapters / graph-only Neo4j adapters
```

All REST routes are prefixed `/api/v1`. Error responses follow RFC 7807 `ProblemDetail`.

### Four Main Controllers

| Controller | Responsibility |
|---|---|
| `SchemaController` | CRUD, generation, review-only multi-source discovery, example generation, validation, knowledge-base schema listing, and activation of JSON schemas |
| `KnowledgeBaseController` | Knowledge base lifecycle |
| `DocumentController` | Upload, dedup, list, replace, delete, chunk retrieval, and trigger processing |
| `QueryController` | Cypher generation, validation, execution, and `/ask` Q&A |
| `RuntimeSettingsController` | List, update, and clear allowlisted runtime setting overrides |
| `AiProfileController` | CRUD for OpenAI-compatible AI profiles with write-only API keys |

### Core Services

- **`SchemaRegistryService`** — parse/validate/store schema versions; `name + version` identity is immutable; inactive schemas can be replaced or deleted under guard.
- **`SchemaDraftEvaluationService`** — durable held-out dry extraction and deterministic draft metrics without graph writes.
- **`SchemaDraftPublicationService`** — revision-specific readiness and atomic inactive-schema publication.
- **`SchemaReprocessingPlanService`** — durable bounded post-activation orchestration over overwrite processing.
- **`DocumentUploadService`** — multipart upload with SHA-256 dedup; stores binary to filesystem via `BinaryStorageService` interface; replace/delete paths clean document-scoped artifacts.
- **`DocumentProcessingService`** — orchestrates: parse → chunk → embed → graph-extract → persist.
- **`GraphExtractionService`** — LLM-based entity/relationship extraction constrained by the active schema; validates against schema before any DB write.
- **`CypherGenerationService`** — LLM prompt-to-Cypher using active schema as context.
- **`CypherValidationService`** — multi-stage safety: blocked keywords → schema label/rel/property check → Neo4j `EXPLAIN` → auto-inject `LIMIT`.
- **`CypherExecutionService`** — read-only Cypher execution.
- **`SchemaBootstrapService`** — loads `src/main/resources/schemas/*.json` on startup.
- **`AiObservationService`** — AI workflow spans, model call metrics, token counters, and privacy-controlled content metadata.
- **`RuntimeSettingsService`** — persisted allowlisted runtime setting overrides, restart lifecycle metadata, live logging control, expanded configuration catalog, and typed live accessors.
- **`SchemaDiscoveryService`** — bounded owned-document/text/file analysis using the active knowledge-base AI profile, deterministic conflict-aware aggregation, and a stateless review-only schema projection.
- **`AiProfileService`** — OpenAI-compatible profile CRUD, default profile seeding from `app.model.*`, API-key masking, and profile cache invalidation.
- **`AiRuntimeModelFactory`** — profile/revision-scoped Spring AI OpenAI chat and embedding model creation.

Application logging is metadata-first and independent from AI observation content capture. Normal logs may include identifiers, lengths, counts, timings, statuses, exception classes, and non-reversible fingerprints, but must not include document text, prompts, queries, model responses, generated schemas, or extracted graph payloads. Controlled content belongs only in `AiObservationService` traces under explicit capture settings.

### Document Ingestion Pipeline

```
Upload → SHA-256 dedup → Filesystem storage
  → Parse (TXT/PDF/DOCX via Tika) → Chunk (with overlap)
  → Embed (vector) → Persist DocumentChunk + vector index
  → Graph extraction (LLM, constrained by schema)
  → Validate extracted nodes/relationships
  → Upsert to Neo4j with provenance metadata
```

### Query / Q&A Pipeline

```
Question → LLM Cypher generation → Multi-stage validation → Read-only execution → Return rows
```

### LLM Client Interfaces

`EmbeddingClient`, `CypherGenerationClient`, and `GraphExtractionClient` are interfaces. Real implementations are Spring beans swapped by profile. Tests inject deterministic mock implementations — the full E2E flow runs without any external API calls.

AI profiles are also resolved at runtime per knowledge base. Document processing, graph extraction, Cypher generation, `/ask`, hybrid search, and knowledge-base-scoped schema generation use the active knowledge-base profile. Profile API keys are write-only: reads expose configured/masked metadata only. Profiles may declare `cl100k_base`; known OpenAI embedding models resolve to it automatically and unknown models use the versioned conservative `utf8-byte-v1` estimator.

## Spring Profiles

| Profile | Effect |
|---|---|
| *(default)* | AI auto-config disabled; embedding/chat endpoints error if invoked |
| `openai` | Spring AI OpenAI enabled; requires `OPENAI_API_KEY` env var |
| `lm_studio` | OpenAI-compatible; requires `LM_STUDIO_API_KEY=lm-studio` |
| `langfuse` | Enables AI observability and exports OTLP traces to local Langfuse defaults |

Startup model properties under `app.model.*` seed the PostgreSQL-backed default AI profile when no default profile exists. New knowledge bases are assigned that default profile. Runtime setting overrides are persisted in PostgreSQL and may change allowlisted query, hybrid search, chunking, extraction, AI observability, and root logging behavior without restart. Canonical chunking settings are strategy, target tokens, overlap tokens, and a hard character limit; legacy max-token/max-character aliases remain readable with canonical precedence. Updates affect subsequent processing only, while runs and chunks retain versioned strategy/settings/tokenizer provenance.

Runtime settings use `mutable=true` to mean editable through the settings API; `liveApplied`, `updateMode`, `activeValue`, and `lifecycleState` describe whether the saved value applies immediately or after restart. Supported non-secret restart-required settings such as `app.storage.documents-root` may be persisted as desired values and reported as `pending-restart` until the backend restarts with that value active. The runtime settings list also exposes profile-resolved startup defaults for read-only, restart-required, profile-managed, and sensitive-read-only configuration inventory. Covered groups include application identity, Spring AI bootstrap/OpenAI aliases, Spring auto-configuration, PostgreSQL datasource/schema/pool metadata, Neo4j, storage, multipart, actuator/health, tracing, and OpenTelemetry exporter settings. Settings consumed before PostgreSQL-backed overrides can load remain deployment-managed unless a safe runtime reassignment path exists; PostgreSQL and Neo4j connectivity, credentials, database/schema selection, and pool metadata stay deployment-managed through environment variables, Docker Compose, or equivalent configuration. AI provider behavior changes go through AI profile management, not raw `app.model.*` or `spring.ai.openai.*` updates. API keys, datasource/Neo4j passwords, and OTLP authorization headers are masked in read responses.

## Configuration

Key config files:
- `src/main/resources/application.properties` — base settings (Neo4j URI, storage path, chunking params, query safety rules, extraction limits)
- `src/main/resources/application-openai.properties` / `application-lm_studio.properties` — profile overrides
- `src/main/resources/application-langfuse.properties` — local Langfuse observability profile
- `src/main/resources/schemas/*.json` — predefined bootstrap schemas (`legal-contracts-v1`, `cmms-v1`)
- `compose.yaml` — Neo4j and optional local Langfuse stack via Docker Compose profiles

All application config is bound to `AppProperties` (validated `@ConfigurationProperties` record).

## Testing Approach

- `./mvnw test -Pfast` excludes the shared integration tag; `./mvnw test` remains complete and credential-free.
- Application integration tests share one JVM-scoped PostgreSQL and Neo4j container, reset state before each test, and remain sequential.
- Fresh-server provisioning and startup tests retain independent PostgreSQL containers.
- `./scripts/measure-test-suite.sh` writes timing, inventory, context, container-start, and slowest-test reports under `target/test-performance`.
- Mock AI clients return hardcoded deterministic results, making tests independent of external APIs.
- `EndToEndMvpFlowIntegrationTest` is the canonical example of the complete pipeline under test.

## Persistence Operations

PostgreSQL owns profiles, settings, knowledge bases, schemas, documents, runs,
draft workflows, publications, and reprocessing. Neo4j owns only chunks/embeddings,
schema-defined facts, evidence, provenance, direct scope, and graph-native
relationships. Local Langfuse uses a separate `langfuse` database and role on the
shared PostgreSQL server. Langfuse event and media objects use pinned Garage
`v2.3.0` with separate persistent metadata and data volumes, an idempotent
initializer, an internal event endpoint, and a host-reachable media endpoint.
GraphRAG document and schema-draft binaries remain on the configured local
filesystem.

The `langfuse-postgres` service is ignored for Spring Boot service-connection
discovery, so explicit
`GRAPHRAG_POSTGRES_*` settings route GraphRAG to `graphrag / graphrag / app`.
Never run `docker compose down -v`, delete the `langfuse_postgres_data` volume, or
drop the `langfuse` database. Preserve both `langfuse_garage_meta` and
`langfuse_garage_data`. Back up both Garage volumes consistently and
regularly verify that the pair can be restored.

## Key Design Decisions

- **Immutable schema identity:** once a `name + version` is persisted, that identity cannot change. Inactive schema content can be replaced when the replacement keeps the same identity; active schemas cannot be updated or deleted.
- **Document mutation cleanup:** replacing or deleting a document must remove its chunks, extraction runs, graph relationships, obsolete extracted nodes, and local binary content.
- **Schema-driven extraction:** LLM is explicitly constrained to only extract node labels and relationship types defined in the active schema.
- **Provider-agnostic AI:** storage, embedding, generation, and extraction are all behind interfaces to allow swapping providers or using mocks.
- **Profile-scoped AI:** knowledge bases carry an active AI profile; profile changes are rejected when embedding provider/model/dimension or resolved-tokenizer metadata is incompatible with existing chunks.
- **Runtime settings with an allowlist:** runtime overrides must go through `RuntimeSettingsService` typed accessors or explicit apply paths, not ad hoc property reads; catalog entries expose editability, update mode, live-apply status, lifecycle state, sensitivity, and rejection or restart reasons without making unsafe startup-bound infrastructure mutable.
- **Read-only query safety:** `CypherValidationService` enforces blocked mutating keywords and auto-injects `LIMIT` before any query is executed.
- **No Java `var`:** declare concrete variable types explicitly instead of using the `var` keyword.

## Documentation Hygiene

When updating shared implementation facts in `README.md`, `AGENTS.md`, or `CLAUDE.md`, keep the overlapping guidance aligned in the same change so contributors do not receive conflicting instructions.
