# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tech Stack

- **Language:** Java 25, **Framework:** Spring Boot 4.0.6
- **Database:** Neo4j 5 (graph + vector index via Spring Data Neo4j)
- **LLM Integration:** Spring AI 2.0 (OpenAI-compatible) + LangChain4j 1.14
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

# Run with local LM Studio
LM_STUDIO_API_KEY=lm-studio ./mvnw spring-boot:run -Dspring-boot.run.profiles=lm_studio

# All tests (Testcontainers spins up Neo4j automatically)
./mvnw test

# Single test class
./mvnw -Dtest=EndToEndMvpFlowIntegrationTest test

# Start Neo4j only
docker compose up -d neo4j
```

Neo4j default credentials (dev): `neo4j / notverysecret`, ports `7474` (HTTP) and `7687` (Bolt).

## Architecture

### Layers

```
REST Controllers  →  Service Layer  →  Repository (Spring Data Neo4j)  →  Neo4j
```

All REST routes are prefixed `/api/v1`. Error responses follow RFC 7807 `ProblemDetail`.

### Four Main Controllers

| Controller | Responsibility |
|---|---|
| `SchemaController` | CRUD, generation, validation, and activation of YAML schemas |
| `KnowledgeBaseController` | Knowledge base lifecycle |
| `DocumentController` | Upload, dedup, and trigger processing |
| `QueryController` | Cypher generation, validation, execution, and `/ask` Q&A |

### Core Services

- **`SchemaRegistryService`** — parse/validate/store schema versions; `name + version` is immutable once saved.
- **`DocumentUploadService`** — multipart upload with SHA-256 dedup; stores binary to filesystem via `BinaryStorageService` interface.
- **`DocumentProcessingService`** — orchestrates: parse → chunk → embed → graph-extract → persist.
- **`GraphExtractionService`** — LLM-based entity/relationship extraction constrained by the active schema; validates against schema before any DB write.
- **`CypherGenerationService`** — LLM prompt-to-Cypher using active schema as context.
- **`CypherValidationService`** — multi-stage safety: blocked keywords → schema label/rel/property check → Neo4j `EXPLAIN` → auto-inject `LIMIT`.
- **`CypherExecutionService`** — read-only Cypher execution.
- **`SchemaBootstrapService`** — loads `src/main/resources/schemas/*.yaml` on startup.

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

## Spring Profiles

| Profile | Effect |
|---|---|
| *(default)* | AI auto-config disabled; embedding/chat endpoints error if invoked |
| `openai` | Spring AI OpenAI enabled; requires `OPENAI_API_KEY` env var |
| `lm_studio` | OpenAI-compatible; requires `LM_STUDIO_API_KEY=lm-studio` |

## Configuration

Key config files:
- `src/main/resources/application.properties` — base settings (Neo4j URI, storage path, chunking params, query safety rules, extraction limits)
- `src/main/resources/application-openai.properties` / `application-lm_studio.properties` — profile overrides
- `src/main/resources/schemas/*.yaml` — predefined bootstrap schemas (`legal-contracts-v1`, `cmms-v1`)
- `compose.yaml` — Neo4j via Docker Compose

All application config is bound to `AppProperties` (validated `@ConfigurationProperties` record).

## Testing Approach

- Integration tests use **Testcontainers** — Neo4j container is started automatically; no manual setup needed.
- Mock AI clients return hardcoded deterministic results, making tests independent of external APIs.
- `EndToEndMvpFlowIntegrationTest` is the canonical example of the complete pipeline under test.

## Key Design Decisions

- **Immutable schema versions:** once a `name + version` is persisted, the content cannot be updated — only new versions can be added.
- **Schema-driven extraction:** LLM is explicitly constrained to only extract node labels and relationship types defined in the active schema.
- **Provider-agnostic AI:** storage, embedding, generation, and extraction are all behind interfaces to allow swapping providers or using mocks.
- **Read-only query safety:** `CypherValidationService` enforces blocked mutating keywords and auto-injects `LIMIT` before any query is executed.
- **No Java `var`:** declare concrete variable types explicitly instead of using the `var` keyword.
