# GraphRAG

GraphRAG is a schema-managed knowledge-base API that ingests documents, creates embeddings and schema-constrained graph facts, executes validated read-only Cypher, and provides durable cited advanced search.

The [multipage documentation portal](src/site/markdown/index.md) is the canonical detailed guide for users, operators, integrators, and contributors. On GitHub, use the current [`main` portal](https://github.com/vfedoriv/graphrag/blob/main/src/site/markdown/index.md). The [graphrag-ui repository](https://github.com/vfedoriv/graphrag-ui) owns frontend controls, screenshots, and browser behavior.

## Stack

- Java 25 and Spring Boot 4.1.0
- PostgreSQL 17 for all operational state (Flyway-managed `app` schema)
- Neo4j 5 for chunks/vectors, graph facts, evidence, and provenance
- Spring AI 2.0.0 and LangChain4j 1.16.2
- OpenTelemetry and Micrometer AI observability, with optional local Langfuse
- Maven Wrapper (`./mvnw`)

## Quick start

Prerequisites: JDK 25, Docker with Compose, and an OpenAI-compatible provider for AI-backed workflows.

```bash
docker compose up -d langfuse-postgres neo4j
docker compose exec -T langfuse-postgres \
  bash /docker-entrypoint-initdb.d/20-init-graphrag.sh

OPENAI_API_KEY=<key> ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=openai

curl http://localhost:8080/actuator/health
```

The default profile can boot without AI credentials:

```bash
./mvnw spring-boot:run
```

See [prerequisites and local setup](src/site/markdown/getting-started/local-setup.md) and the [first end-to-end run](src/site/markdown/getting-started/first-run.md).

## Documentation and API

```bash
./mvnw site
./mvnw site:run
```

Maven generates the portal under `target/site`; local preview needs no Node or Python toolchain. Runtime contracts are available at:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Curated API map: [src/site/markdown/reference/api.md](src/site/markdown/reference/api.md)

Major workflow guides:

- [Knowledge bases and AI profiles](src/site/markdown/workflows/knowledge-bases-profiles.md)
- [Schema registry](src/site/markdown/workflows/schemas.md) and [schema drafts](src/site/markdown/workflows/schema-drafts.md)
- [Document processing](src/site/markdown/workflows/document-processing.md)
- [Chunking and reprocessing](src/site/markdown/workflows/chunking-reprocessing.md)
- [Cypher query safety](src/site/markdown/workflows/cypher-queries.md)
- [Durable advanced search](src/site/markdown/workflows/advanced-search.md)
- [Configuration/runtime settings](src/site/markdown/operations/configuration.md) and [observability/privacy](src/site/markdown/operations/observability.md)

## Build and test

```bash
./mvnw clean package
./mvnw test -Pfast
./mvnw test
./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest
```

The complete suite is credential-free but requires Docker for shared Testcontainers PostgreSQL and Neo4j. See the [testing guide](src/site/markdown/contributing/testing.md).

## Core safety boundaries

- Schema identity (`name + version`) is immutable; active schemas cannot be updated or deleted.
- Profile assignment rejects embedding provider/model/dimension/resolved-tokenizer incompatibility once chunks exist.
- Document replacement/deletion cleans document-scoped binaries and derived artifacts.
- Extraction stays within the active schema.
- Cypher is validated, `EXPLAIN`ed, bounded, and executed read-only.
- Application logs are metadata-first; opt-in AI observation content follows explicit privacy controls.

For architecture, persistence ownership, production readiness, and contribution guidance, start at the [portal index](src/site/markdown/index.md).

Reprocessing preparation, execution, and recovery use schemas-owned ports mapped
by bootstrap integration adapters to documents-owned capabilities. Documents
owns selection summaries, option/parser resolution, chunk/run classification,
target inspection, source checks, migration input restoration, and profile scope.
Schemas retains selection policy, durable snapshots, plan claims, completion,
and retry policy. Architecture checks enforce the completed reprocessing boundary
without preparation exceptions. AI compatibility uses AI-owned rules and stored-observation ports; document ownership is consolidated; see the
[architecture boundary](src/site/markdown/concepts/architecture.md#reprocessing-execution-and-recovery-boundary).

Knowledge-base deletion reads document counts and requests scoped cleanup through
`knowledgebase.ports`, mapped under `bootstrap.integration.knowledgebase` to
`KnowledgeBaseDocumentsFacade`. AI owns deterministic embedding identity/tokenizer
compatibility under `ai.domain` and admission through `EmbeddingCompatibility`;
`StoredEmbeddingsFacade` supplies raw observations through `ai.ports` and
`bootstrap.integration.ai`. Profile assignment presence/IDs come from
`AiProfileAssignmentsFacade`; AI profile persistence reads only profiles.
Synchronous count/assignment reads join caller transactions. Cleanup failure
prevents relational deletion but may leave earlier external effects.
`EmbeddingSpacePolicy` remains a delegating bridge only for
`AdvancedSearchReadinessService` and `DenseTextRetriever` (roadmap step 8);
architecture tests freeze its exact callers. Document processing and migration
preparation use AI-owned `EmbeddingCompatibility` and immutable `EmbeddingTarget`.

Document ownership is consolidated under `documents`: API entry points and models
in `api`, management and processing workflows in `application`, deterministic
values/rules in `domain`, effect and persistence contracts in `ports`, and owned
relational, graph, parsing, model, chunking, and binary integrations in `adapters`.
`bootstrap.DocumentsProcessingConfiguration` assembles processing stages.
Shared draft binary storage remains in `storage`. Relational checkpoints remain
separate from external processing; there is no enclosing cross-store transaction.
Exact transitional dependencies carry retirement steps 5–9 in
`ArchitectureBoundaryTest`; schema registry/discovery boundaries (step 5) remain
pending.
