# AGENTS.md

This file provides guidance to coding agents working in this repository.

## Stack

- Java 25
- Spring Boot 4.0.6
- Neo4j 5 (graph + vector index)
- Spring AI 2.0 (OpenAI-compatible) + LangChain4j 1.14
- Maven Wrapper (`./mvnw`)

## Commands

```bash
# Build
./mvnw clean package

# Run (default profile, no AI provider configured)
./mvnw spring-boot:run

# Run with OpenAI profile
OPENAI_API_KEY=<key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=openai

# Run with LM Studio profile
LM_STUDIO_API_KEY=lm-studio ./mvnw spring-boot:run -Dspring-boot.run.profiles=lm_studio

# Tests
./mvnw test
./mvnw -Dtest=EndToEndMvpFlowIntegrationTest test

# Neo4j only
docker compose up -d neo4j
```

Use `./mvnw` instead of bare `mvn`.

## Architecture

- API prefix: `/api/v1`
- Error format: RFC 7807 `ProblemDetail`
- Layering: Controllers -> Services -> Repositories -> Neo4j

Main controllers:
- `SchemaController`
- `KnowledgeBaseController`
- `DocumentController`
- `QueryController`

Key services:
- `SchemaRegistryService` (schema parse/validate/versioning)
- `DocumentUploadService` (multipart upload + SHA-256 dedup)
- `DocumentProcessingService` (parse -> chunk -> embed -> graph extract -> persist)
- `GraphExtractionService` (schema-constrained extraction + validation)
- `CypherGenerationService`
- `CypherValidationService` (blocked keywords + schema checks + `EXPLAIN` + auto `LIMIT`)
- `CypherExecutionService`
- `SchemaBootstrapService` (loads bootstrap schemas on startup)

## Profiles

- `default`: no AI provider auto-config
- `openai`: requires `OPENAI_API_KEY`
- `lm_studio`: requires `LM_STUDIO_API_KEY=lm-studio`

## Key Files

- `src/main/resources/application.properties`
- `src/main/resources/application-openai.properties`
- `src/main/resources/application-lm_studio.properties`
- `src/main/resources/schemas/*.yaml`
- `compose.yaml`

## Testing

- Integration tests use Testcontainers (Neo4j started automatically)
- AI clients are mocked for deterministic tests
- Canonical full-flow integration test: `EndToEndMvpFlowIntegrationTest`

## OpenSpec Workflow

OpenSpec artifacts are the source of historical product decisions. Archived changes under `openspec/changes/archive` document completed implementation work. For new behavior or contract changes, add or update OpenSpec specs before implementation when the change is non-trivial.

## Commit & Pull Request Guidelines

Use short imperative commit messages such as `add schema activation panel`. Keep commits focused and reviewable. Do not mention in commit messages "openspec" unless the user explicitly asks about it.

## Design Constraints

- Schema versions are immutable (`name + version` cannot be updated after save)
- Extraction must stay constrained to active schema labels/relationship types
- Query execution is read-only and validated before run
- Do not use the Java `var` keyword; declare the concrete variable type explicitly.
