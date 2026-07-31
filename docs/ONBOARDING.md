# GraphRAG Onboarding Guide

## Project Overview

GraphRAG is a Java 25 and Spring Boot 4 REST API for schema-managed knowledge bases in Neo4j. It supports document upload, parsing, chunking, embedding, schema-constrained graph extraction, and schema-aware Cypher Q&A.

Core technologies:

- Java 25
- Spring Boot 4
- Neo4j graph storage and vector indexes
- Spring AI and LangChain4j for model integrations
- OpenTelemetry and Micrometer AI observability
- Maven Wrapper for build and test execution

The application is API-first and synchronous. The main safety theme is that model-produced outputs are validated before they affect persisted graph data or query execution.

## Local Workflow

Use the Maven Wrapper from the repository root:

```bash
./mvnw test
./mvnw clean package
./mvnw spring-boot:run
```

Useful focused test commands:

```bash
./mvnw -Dtest=EndToEndMvpFlowIntegrationTest test
./mvnw -Dtest=DocumentProcessingIntegrationTest test
./mvnw -Dtest=GraphExtractionCleanupIntegrationTest test
```

Neo4j can be started locally with:

```bash
docker compose up -d neo4j
```

## Architecture Layers

### API Layer

HTTP controllers and DTOs live under `src/main/java/io/github/vfedoriv/graphrag/controller` and `dto`.

Key files:

- `DocumentController.java` handles upload, replace, delete, process, list, and chunk retrieval endpoints.
- `KnowledgeBaseController.java` manages knowledge base CRUD.
- `SchemaController.java` manages schema create/list/get/update/delete/validate/activate and schema generation endpoints.
- `QueryController.java` exposes generate, validate, execute, and ask query workflows.

Controllers are intentionally thin: they log request metadata, delegate to services, and map domain nodes to response DTOs.

### Application Services

Business workflows live in `service`, `document`, `embedding`, `graph`, `query`, `storage`, and `observability`.

Key files:

- `SchemaRegistryService.java` parses, validates, versions, persists, and activates schemas.
- `DocumentUploadService.java` handles multipart upload, SHA-256 deduplication, replacement, deletion, and artifact cleanup.
- `DocumentProcessingService.java` orchestrates parse -> chunk -> embed -> persist chunks -> graph extraction.
- `GraphExtractionService.java` coordinates schema-constrained extraction for persisted chunks.
- `CypherGenerationService.java` generates Cypher from natural language and active schema context.
- `CypherValidationService.java` enforces read-only, schema-aware Cypher safety.
- `CypherExecutionService.java` executes only validated Cypher and normalizes Neo4j values.
- `AiObservationService.java` centralizes AI workflow and model-call telemetry.

### Domain and Schema Model

Domain nodes and schema parsing support live under `domain` and `schema`.

Key files:

- `KnowledgeBaseNode.java` stores knowledge base metadata and active schema reference.
- `SchemaDefinitionNode.java` stores schema identity, content, status, and version metadata.
- `DocumentUploadNode.java` stores uploaded document metadata and processing status.
- `DocumentChunkNode.java` stores ordered chunk text, token estimate, embedding, and metadata.
- `ExtractionRunNode.java` tracks graph extraction runs.
- `SchemaDocument.java`, `SchemaParser.java`, and `SchemaValidator.java` define and validate schema contracts.

Important invariant: schema identity is immutable. The `name + version` pair cannot be changed after save.

### Persistence

Repository interfaces live under `repository`.

Key files:

- `KnowledgeBaseRepository.java`
- `SchemaDefinitionRepository.java`
- `DocumentUploadRepository.java`
- `DocumentChunkRepository.java`
- `ExtractionRunRepository.java`

Most custom graph cleanup and relationship writes use `Neo4jClient` in service classes rather than repository methods.

### Configuration and Error Handling

Configuration and API error handling live under `config`, `error`, and `src/main/resources`.

Key files:

- `AppProperties.java` defines validated application configuration.
- `GlobalExceptionHandler.java` maps exceptions to RFC 7807-style responses.
- `application.properties` and profile-specific property files define default, OpenAI, LM Studio, and Langfuse behavior.
- `compose.yaml` defines local Neo4j and optional Langfuse infrastructure.

### Tests

Tests cover unit, MVC, and integration behavior with mocked AI clients and Testcontainers-backed Neo4j.

Key tests:

- `EndToEndMvpFlowIntegrationTest.java` is the canonical full-flow test.
- `DocumentProcessingIntegrationTest.java` verifies chunk persistence, embeddings, vector index creation, and extraction.
- `GraphExtractionCleanupIntegrationTest.java` verifies overwrite and cleanup behavior.
- `SchemaRegistryIntegrationTest.java` verifies schema lifecycle rules.
- `CypherValidationServiceTest.java` and `CypherExecutionServiceTest.java` cover query safety and execution behavior.

## Key Concepts

### Schema-Managed Graph Extraction

Knowledge bases use active schemas to constrain extraction and query generation. Extraction must stay inside active schema labels and relationship types. Query generation also uses active schema context, then validation checks labels, relationship types, properties, blocked keywords, and `EXPLAIN`.

Start with:

- `SchemaController.java`
- `SchemaRegistryService.java`
- `SchemaParser.java`
- `SchemaValidator.java`

### Document Lifecycle

Uploaded documents are stored locally and represented in Neo4j as `DocumentUpload` nodes. Replacement and deletion are expected to clean all document-scoped artifacts: chunks, extraction runs, graph relationships, obsolete extracted nodes, and binary content.

Start with:

- `DocumentController.java`
- `DocumentUploadService.java`
- `DocumentUploadRepository.java`

### Chunking and Embeddings

Processing reads the stored binary, parses text, chunks it with configured token/character and overlap settings, embeds each child chunk, saves `DocumentChunk` nodes, and creates `DocumentUpload -[:HAS_CHUNK]-> DocumentChunk` relationships. Advanced search uses knowledge-base-scoped vector and lexical indexes over those child chunks.

Start with:

- `DocumentProcessingService.java`
- `DocumentParsingService.java`
- `ChunkingService.java`
- `DocumentChunkNode.java`

### Graph Extraction and Provenance

Persisted extraction parents are passed to graph extraction. Canonical nodes and relationships resolve through `GraphExtractionEvidence`, which records knowledge-base, source-document, source-chunk, processing-run, revision, and bounded source-range provenance.

Start with:

- `GraphExtractionService.java`
- `GraphExtractionValidationService.java`
- `GraphWriteService.java`
- `GraphWriteSupport.java`

### Query Flow

The query API has five surfaces:

- Generate Cypher from a prompt.
- Validate submitted Cypher.
- Execute validated Cypher.
- Ask, which combines generate, validate, and execute.
- Durable advanced search, which supports submission, polling, result retrieval, cancellation, cited answers, and partial branch-failure results.

The service only executes validator-approved Cypher. Generated Cypher is not trusted by default.

Start with:

- `QueryController.java`
- `AdvancedSearchRunController.java`
- `AdvancedSearchRunService.java`
- `DefaultAdvancedSearchRunProcessor.java`
- `CypherGenerationService.java`
- `CypherValidationService.java`
- `CypherExecutionService.java`
- `SpringAiCypherGenerationClient.java`

### AI Observability

AI workflows emit spans and model-call metadata. Content metadata is privacy-controlled. This covers embedding, extraction, schema generation, and query generation paths.

Start with:

- `AiObservationService.java`
- `AiModelCallObservation.java`
- `AiWorkflowContext.java`
- `SpringAiEmbeddingClient.java`
- `SpringAiGraphExtractionClient.java`

## Guided Tour

1. Project overview
   Read `README.md` and `AGENTS.md` to understand project scope, constraints, and workflow expectations.

2. Application bootstrap
   Inspect `GraphragApplication.java`, `application.properties`, and `pom.xml` to understand runtime profiles and dependencies.

3. Schema and knowledge base API
   Follow `SchemaController.java`, `SchemaRegistryService.java`, `SchemaParser.java`, and `SchemaDefinitionRepository.java`.

4. Document ingestion pipeline
   Trace `DocumentController.java` into `DocumentUploadService.java`, `DocumentProcessingService.java`, and `GraphExtractionService.java`.

5. Query flow
   Review `QueryController.java`, `CypherGenerationService.java`, `CypherValidationService.java`, and `CypherExecutionService.java`.

6. Verification coverage
   Read `EndToEndMvpFlowIntegrationTest.java`, `DocumentProcessingIntegrationTest.java`, and `GraphExtractionCleanupIntegrationTest.java`.

## File Map

### Controllers

- `src/main/java/io/github/vfedoriv/graphrag/controller/DocumentController.java`: document upload, replacement, deletion, processing, and chunk retrieval.
- `src/main/java/io/github/vfedoriv/graphrag/controller/KnowledgeBaseController.java`: knowledge base CRUD.
- `src/main/java/io/github/vfedoriv/graphrag/controller/SchemaController.java`: schema lifecycle, validation, activation, and generation.
- `src/main/java/io/github/vfedoriv/graphrag/controller/QueryController.java`: Cypher generation, validation, execution, and one-shot ask.

### Services

- `src/main/java/io/github/vfedoriv/graphrag/service/SchemaRegistryService.java`: schema parse/validate/version/activate rules.
- `src/main/java/io/github/vfedoriv/graphrag/service/DocumentUploadService.java`: upload storage, deduplication, replacement, deletion, cleanup.
- `src/main/java/io/github/vfedoriv/graphrag/service/DocumentProcessingService.java`: parse, chunk, embed, persist chunks, launch graph extraction.
- `src/main/java/io/github/vfedoriv/graphrag/service/GraphExtractionService.java`: extraction run orchestration.
- `src/main/java/io/github/vfedoriv/graphrag/graph/GraphWriteService.java`: extracted graph writes and provenance links.
- `src/main/java/io/github/vfedoriv/graphrag/service/CypherGenerationService.java`: prompt-to-Cypher generation.
- `src/main/java/io/github/vfedoriv/graphrag/service/CypherValidationService.java`: read-only and schema-aware query validation.
- `src/main/java/io/github/vfedoriv/graphrag/service/CypherExecutionService.java`: validated Cypher execution and result normalization.

### Domain and Schema

- `src/main/java/io/github/vfedoriv/graphrag/domain/KnowledgeBaseNode.java`: knowledge base metadata.
- `src/main/java/io/github/vfedoriv/graphrag/domain/SchemaDefinitionNode.java`: saved schema metadata and content.
- `src/main/java/io/github/vfedoriv/graphrag/domain/DocumentUploadNode.java`: uploaded document metadata and status.
- `src/main/java/io/github/vfedoriv/graphrag/domain/DocumentChunkNode.java`: chunk text, embedding, metadata, and ordering.
- `src/main/java/io/github/vfedoriv/graphrag/schema/SchemaDocument.java`: schema model.
- `src/main/java/io/github/vfedoriv/graphrag/schema/SchemaValidator.java`: schema validation rules.

### Configuration

- `src/main/resources/application.properties`: baseline app configuration.
- `src/main/resources/application-openai.properties`: OpenAI profile configuration.
- `src/main/resources/application-lm_studio.properties`: LM Studio profile configuration.
- `src/main/resources/application-langfuse.properties`: local Langfuse observability profile.
- `compose.yaml`: local Neo4j and optional Langfuse services.
- `pom.xml`: Maven dependencies and versions.

## Complexity Hotspots

Approach these areas carefully:

- `SchemaRegistryService.java`: enforces schema identity, active/inactive mutation rules, validation, and activation.
- `DocumentUploadService.java`: owns artifact cleanup semantics for replace/delete.
- `DocumentProcessingService.java`: coordinates parsing, chunking, embeddings, vector index setup, chunk persistence, and graph extraction.
- `GraphExtractionService.java`: orchestrates schema-constrained extraction and overwrite cleanup.
- `GraphWriteService.java`: writes extracted entities/relationships and provenance into Neo4j.
- `GraphExtractionValidationService.java`: validates extracted graph payloads against schema constraints.
- `CypherValidationService.java`: central query safety gate.
- `AiObservationService.java`: broad observability surface with privacy-controlled metadata.
- `SchemaController.java`, `DocumentController.java`, and `QueryController.java`: large API controllers with many endpoint contracts.

## Practical First Tasks

Good starter tasks:

- Add or adjust a DTO validation rule and its controller test.
- Add a focused unit test around `ChunkingService` or `CypherValidationService`.
- Improve README or OpenAPI examples for an existing endpoint.
- Add a schema fixture under `src/main/resources/schemas`.

Higher-risk tasks:

- Changing schema identity or activation semantics.
- Changing document replacement/deletion cleanup.
- Changing graph extraction validation or graph write provenance.
- Changing query validation rules.
- Changing AI observability content metadata behavior.

## Review Checklist

Before opening a PR:

- Run `./mvnw test`.
- For document processing changes, run `./mvnw -Dtest=DocumentProcessingIntegrationTest test`.
- For full-flow changes, run `./mvnw -Dtest=EndToEndMvpFlowIntegrationTest test`.
- Keep README, AGENTS.md, and CLAUDE.md synchronized when changing shared contributor guidance.
- Do not use Java `var`; declare concrete variable types.
- Preserve RFC 7807 `ProblemDetail` error responses.
- Keep query execution read-only and validated before execution.
