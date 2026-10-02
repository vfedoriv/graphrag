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
Search consolidation (roadmap step 8) is implemented under `search.query`,
`search.retrieval`, `search.ranking`, `search.answering`, and `search.runs`. Search
owns query and advanced-search APIs, workflows, deterministic policy, query and
retrieval effects, model adapters, and durable run persistence. Document metadata
is supplied through `DocumentMetadataAccess`: citation lookup is capped at 128
IDs and metadata selection at 200 results; `SearchDocumentMetadataAdapter` maps
it to search-owned ports. `SearchKnowledgeBaseAccess` supplies knowledge-base existence and active
schema/profile facts, while `StoredSchemaSnapshots`, `SchemaSnapshots`, and
`CapturedSchemaParsing` supply stored, active, and captured schema facts through
`SearchSchemaAdapter` in `bootstrap.integration.search`. AI owns compatibility
through `EmbeddingCompatibility` and immutable `EmbeddingTarget`; the
`EmbeddingSpacePolicy` bridge has been removed. `ArchitectureBoundaryTest` retires
the step-8 schema, document-metadata, and compatibility exceptions. Exact settings,
AI model/profile construction, observability, logging, transaction, shared
embedding/lexical-index, and configuration-assembly seams remain assigned to
roadmap step 9. Document processing and migration preparation use AI-owned
`EmbeddingCompatibility` and immutable `EmbeddingTarget` directly.

Document ownership is consolidated under `documents`: API entry points and models
in `api`, management and processing workflows in `application`, deterministic
values/rules in `domain`, effect and persistence contracts in `ports`, and owned
relational, graph, parsing, model, chunking, and binary integrations in `adapters`.
`bootstrap.DocumentsProcessingConfiguration` assembles processing stages.
Shared draft binary storage remains in `storage`. Relational checkpoints remain
separate from external processing; there is no enclosing cross-store transaction.
Schema registry and discovery ownership (step 5) is implemented under
`schemas.registry` and `schemas.discovery`. Immutable `schemas.contracts` snapshots
feed document extraction; knowledge-base associations and document source inputs
are accessed through public capabilities and mapping-only bootstrap adapters.
Schema draft authoring (step 6) is now owned by `schemas.drafts` across API,
application, domain, ports, and relational/binary adapters. Lifecycle, source
revision and storage recovery, durable analysis, review, conflicts, and draft
history use schema-owned persistence. `DraftDocumentInputs` obtains scoped
metadata/fingerprints and content/parsing through the documents-owned
`DocumentSourceInputs` capability. Loaded document bytes are checked against the
captured source hash before parsing. `DraftSchemaLookup` reads immutable stored
schema facts without parsing; review requests a parsed definition separately; `DraftKnowledgeBases` supplies managed-knowledge-base
admission, active schema ID, and non-secret active profile ID/revision facts.
Mapping adapters under
`bootstrap.integration.schemas` expose no persistence records, paths, clients, or
secrets and add no transactions; synchronous reads join the caller's transaction.
Draft-owned bytes still use shared storage, while relational checkpoints remain
separate from external work.

Schema evaluation, publication, and reprocessing ownership (step 7) is implemented
under `schemas.evaluation`, `schemas.publication`, and `schemas.reprocessing`.
Each area owns its API/domain values, workflows, checkpoints, and relational
adapters; evaluation and reprocessing also own their history/currentness summaries.
Draft authoring exposes immutable admission, review, contributor, and publication-link
contracts. Document-owned preparation and per-chunk dry extraction are mapped to
evaluation ports by transaction-free bootstrap adapters; dry evaluation writes no
document processing state or graph artifacts. Registry operations and non-secret
knowledge-base/profile facts flow through public immutable contracts. Publication
retains durable intent, inactive registration, and transactional draft linkage.
Draft navigation consumes bounded batch summary ports through mapping-only bridges.
Existing HTTP/SQL/snapshot contracts, fingerprints, race semantics, and recovery
predicates remain unchanged. The exact step-6 and step-7 exceptions are retired;
`ArchitectureBoundaryTest` retains only exact support/assembly (step 9)
exceptions after search consolidation.
