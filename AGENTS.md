# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Stack

- Java 25
- Spring Boot 4.0.6
- Neo4j 5 (graph + vector index)
- Spring AI 2.0 (OpenAI-compatible) + LangChain4j 1.14
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
./mvnw test
./mvnw -Dtest=EndToEndMvpFlowIntegrationTest test

# Neo4j only
docker compose up -d neo4j

# Neo4j + local Langfuse stack
docker compose --profile langfuse up -d
```

Use `./mvnw` instead of bare `mvn`.

## Architecture

- API prefix: `/api/v1`
- Error format: RFC 7807 `ProblemDetail`
- Layering: Controllers -> Services -> Repositories -> Neo4j

Main controllers:
- `SchemaController` (create/list/get/update/delete/validate/activate, schema generation, example generation, KB schema listing)
- `KnowledgeBaseController`
- `DocumentController` (upload/list/replace/delete/process/chunks)
- `QueryController`

Key services:
- `SchemaRegistryService` (schema parse/validate/versioning + guarded inactive-schema update/delete)
- `DocumentUploadService` (multipart upload + SHA-256 dedup + replace/delete artifact cleanup)
- `DocumentProcessingService` (parse -> chunk -> embed -> graph extract -> persist)
- `GraphExtractionService` (schema-constrained extraction + validation)
- `CypherGenerationService`
- `CypherValidationService` (blocked keywords + schema checks + `EXPLAIN` + auto `LIMIT`)
- `CypherExecutionService`
- `SchemaBootstrapService` (loads bootstrap schemas on startup)
- `AiObservationService` (AI workflow spans, model call metrics, privacy-controlled content metadata)
- `RuntimeSettingsService` (allowlisted live runtime setting overrides + typed accessors)
- `AiProfileService` (OpenAI-compatible AI profile CRUD, write-only API keys, default profile seeding)
- `AiRuntimeModelFactory` (profile/revision-scoped Spring AI OpenAI chat and embedding clients)

## Profiles

- `default`: no AI provider auto-config
- `openai`: requires `OPENAI_API_KEY`
- `lm_studio`: requires `LM_STUDIO_API_KEY=lm-studio`
- `langfuse`: enables AI observability and exports OTLP traces to local Langfuse defaults

At startup the default AI profile is seeded from `app.model.*` when no default exists. New knowledge bases receive the default profile. Document processing, extraction, Cypher generation, `/ask`, hybrid search, and KB-scoped schema generation resolve the active knowledge-base AI profile at runtime. Profile API keys are write-only and must not be returned by read APIs.

## Key Files

- `src/main/resources/application.properties`
- `src/main/resources/application-openai.properties`
- `src/main/resources/application-lm_studio.properties`
- `src/main/resources/application-langfuse.properties`
- `src/main/resources/schemas/*.json`
- `compose.yaml`

## Testing

- Integration tests use Testcontainers (Neo4j started automatically)
- AI clients are mocked for deterministic tests
- Canonical full-flow integration test: `EndToEndMvpFlowIntegrationTest`

## OpenSpec Workflow

OpenSpec artifacts are the source of historical product decisions. Archived changes under `openspec/changes/archive` document completed implementation work. For new behavior or contract changes, add or update OpenSpec specs before implementation when the change is non-trivial.

When changing shared contributor guidance in `README.md`, `AGENTS.md`, or `CLAUDE.md`, keep overlapping implementation facts synchronized in the same change.

## Commit & Pull Request Guidelines

Use short imperative commit messages such as `add schema activation panel`. Keep commits focused and reviewable. Do not mention in commit messages "openspec" unless the user explicitly asks about it.

## Design Constraints

- Schema identity is immutable (`name + version` cannot change after save)
- Inactive schema content can be replaced only when the schema identity (`name + version`) is unchanged; active schemas cannot be updated or deleted
- Document replacement/deletion must clean document-scoped chunks, extraction runs, graph relationships, obsolete extracted nodes, and local binary content
- Extraction must stay constrained to active schema labels/relationship types
- Query execution is read-only and validated before run
- Do not use the Java `var` keyword; declare the concrete variable type explicitly.
- Runtime setting overrides must stay allowlisted, validated, and typed; do not add ad hoc stringly-typed setting reads in feature services.
- Knowledge-base AI profile changes must reject embedding model/dimension incompatibility once chunks exist and must leave the previous active profile unchanged on failure.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, invoke the `skill` tool with `skill: "graphify"` before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
