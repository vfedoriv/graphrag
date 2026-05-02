# GraphRAG Knowledge Base Implementation Plan

## Goal

Build a Java 25 / Spring Boot 4 application that exposes a REST API for uploading documents, parsing them, generating embeddings, extracting graph entities and relationships, storing everything in Neo4j, and answering user questions by generating, validating, executing, and explaining Cypher queries.

Authentication and authorization are intentionally out of scope for the first implementation.

## Main Architecture

Use a typical Java enterprise structure:

```text
io.github.vfedoriv.graphrag
  config
  controller
  service
  repository
  domain
  dto
  schema
  document
  embedding
  graph
  query
  llm
  storage
  error
```

Primary runtime components:

- `DocumentController`: upload, fetch metadata, inspect processing status.
- `SchemaController`: create/load/list/validate graph schema definitions.
- `KnowledgeBaseController`: trigger processing and inspect extracted graph content.
- `QueryController`: generate Cypher from prompts, validate Cypher, execute approved queries.
- `DocumentIngestionService`: orchestrates upload, parsing, chunking, embedding, and graph extraction.
- `SchemaRegistryService`: stores and resolves active graph schemas by knowledge base/domain.
- `GraphExtractionService`: turns document chunks into schema-compliant nodes and relationships.
- `EmbeddingService`: creates vectors for chunks and writes them to Neo4j vector indexes.
- `CypherGenerationService`: asks the LLM to generate Cypher using the active schema.
- `CypherValidationService`: validates generated Cypher before execution.
- `CypherExecutionService`: executes read-only validated queries.
- `BinaryStorageService`: stores original uploaded file bytes outside graph records.

## Storage Model

Use Neo4j as the primary persistence layer for metadata, document graph, extracted entities, relations, and vector search. Store original binary document content in a pluggable binary storage backend.

Initial binary storage:

- Local filesystem path, configurable by `app.storage.documents-root`.
- Document metadata stores only a content URI, not binary content.
- Later replacement can be S3, MinIO, database BLOB storage, or another object store without changing ingestion flow.

Neo4j node model:

```text
(:KnowledgeBase {
  id,
  name,
  activeSchemaId,
  createdAt
})

(:SchemaDefinition {
  id,
  name,
  version,
  sourceType,       // PREDEFINED or GENERATED
  format,           // YAML or JSON
  content,
  contentHash,
  status,
  createdAt
})

(:DocumentUpload {
  id,
  knowledgeBaseId,
  originalFilename,
  contentType,
  sizeBytes,
  sha256,
  contentUri,
  status,
  uploadedAt,
  processedAt,
  errorMessage
})

(:DocumentChunk {
  id,
  documentId,
  chunkIndex,
  text,
  tokenEstimate,
  embedding,
  metadata
})

(:ExtractionRun {
  id,
  documentId,
  schemaId,
  model,
  status,
  startedAt,
  completedAt,
  errorMessage
})
```

Relationships:

```text
(:KnowledgeBase)-[:USES_SCHEMA]->(:SchemaDefinition)
(:KnowledgeBase)-[:HAS_DOCUMENT]->(:DocumentUpload)
(:DocumentUpload)-[:HAS_CHUNK]->(:DocumentChunk)
(:DocumentUpload)-[:HAS_EXTRACTION_RUN]->(:ExtractionRun)
(:DocumentChunk)-[:MENTIONS]->(:ExtractedEntity)
(:ExtractionRun)-[:CREATED_NODE]->(:ExtractedEntity)
(:ExtractionRun)-[:CREATED_RELATION]->(:ExtractedRelation)
```

Domain-specific nodes and relationships are created dynamically from the active schema. They should include common audit/provenance properties:

```text
id
sourceDocumentId
sourceChunkIds
schemaId
extractionRunId
confidence
createdAt
```

## Schema Definition Format

Store graph schemas as versioned YAML documents, validated by a Java model and JSON Schema. YAML is easier to author and review than JSON, while still converting cleanly to structured Java records.

Example:

```yaml
name: legal-contracts
version: 1
description: Schema for contract analysis
nodes:
  - label: Contract
    description: A legal contract or agreement
    key: contractId
    properties:
      - name: contractId
        type: string
        required: true
      - name: title
        type: string
      - name: effectiveDate
        type: date
  - label: Party
    description: Organization or person participating in a contract
    key: name
    properties:
      - name: name
        type: string
        required: true
relationships:
  - type: HAS_PARTY
    from: Contract
    to: Party
    description: Contract has a participating party
    properties:
      - name: role
        type: string
indexes:
  - label: Contract
    properties: [contractId]
    unique: true
vectorIndexes:
  - name: document_chunk_embedding
    label: DocumentChunk
    property: embedding
    dimensions: 1536
    similarity: cosine
```

Schema rules:

- Node labels, relationship types, properties, required fields, and indexes are data, not hardcoded Java constants.
- Java code can reference framework labels such as `DocumentUpload`, `DocumentChunk`, and `SchemaDefinition`.
- Domain labels and relationship types are loaded from `SchemaDefinition`.
- Generated schemas must pass structural validation before activation.
- Query generation prompts must include the active schema content and forbid labels/relations outside it.
- Query validation must check that all referenced labels, relationship types, and properties exist in the schema.

Recommended schema storage:

- Bootstrap schemas in `src/main/resources/schemas/*.yaml`.
- Runtime schemas persisted as `SchemaDefinition` nodes in Neo4j.
- Keep schema versions immutable. Updating a schema creates a new version.

## Document Ingestion Flow

1. REST client uploads a multipart document.
2. `DocumentUploadService` calculates SHA-256, stores bytes through `BinaryStorageService`, and creates `DocumentUpload`.
3. If the same hash already exists in the same knowledge base, skip upload and return the existing document record.
4. `DocumentParsingService` uses Apache Tika / LangChain4j Tika parser to extract text and metadata.
5. `ChunkingService` splits text into chunks with stable chunk indexes and source offsets where possible.
6. `EmbeddingService` generates embeddings through an OpenAI-compatible embedding model.
7. Chunks and embeddings are written to Neo4j, backed by a Neo4j vector index.
8. `GraphExtractionService` asks the LLM to extract schema-compliant entities and relationships from chunks.
9. `GraphWriteService` validates extracted payloads against the active schema and writes/upserts graph nodes and relationships.
10. Upload status transitions through `UPLOADED`, `PARSING`, `EMBEDDING`, `EXTRACTING_GRAPH`, `COMPLETED`, or `FAILED`.

MVP constraints:

- Supported document formats: PDF, TXT, DOCX.
- Document processing is synchronous for the first implementation.

## Query Flow

1. Client submits natural language prompt and `knowledgeBaseId`.
2. `CypherGenerationService` loads the active schema and relevant context.
3. LLM generates a read-only Cypher query plus explanation.
4. `CypherValidationService` validates:
   - query is syntactically valid using Neo4j `EXPLAIN`;
   - query is read-only;
   - labels, relationship types, and properties are allowed by the active schema;
   - query uses parameters instead of interpolated user input where applicable;
   - configurable row/time limits are present or injected.
5. Generated Cypher must pass validation before execution.
6. `CypherExecutionService` executes validated query and returns tabular JSON results.

For the first version, reject mutating Cypher keywords such as `CREATE`, `MERGE`, `SET`, `DELETE`, `DETACH`, `REMOVE`, `DROP`, `LOAD CSV`, and procedure calls unless explicitly allowlisted.

## REST API Draft

Schema:

```text
POST   /api/v1/schemas
GET    /api/v1/schemas
GET    /api/v1/schemas/{schemaId}
POST   /api/v1/schemas/{schemaId}/validate
POST   /api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/activate
POST   /api/v1/schemas/generate
```

Knowledge bases:

```text
POST   /api/v1/knowledge-bases
GET    /api/v1/knowledge-bases
GET    /api/v1/knowledge-bases/{knowledgeBaseId}
```

Documents:

```text
POST   /api/v1/knowledge-bases/{knowledgeBaseId}/documents
GET    /api/v1/knowledge-bases/{knowledgeBaseId}/documents
GET    /api/v1/documents/{documentId}
GET    /api/v1/documents/{documentId}/chunks
POST   /api/v1/documents/{documentId}/process
```

Queries:

```text
POST   /api/v1/knowledge-bases/{knowledgeBaseId}/queries/generate
POST   /api/v1/knowledge-bases/{knowledgeBaseId}/queries/validate
POST   /api/v1/knowledge-bases/{knowledgeBaseId}/queries/execute
POST   /api/v1/knowledge-bases/{knowledgeBaseId}/queries/ask
```

`ask` can generate, validate, execute, and return results in one call, but the lower-level endpoints should exist for safer workflows and easier testing.

## Implementation Phases

### Phase 1: Project Foundation

- Create package structure.
- Add application configuration properties for Neo4j, OpenAI-compatible model endpoints, embedding model, chat model, storage root, chunking, query limits, and extraction limits.
- Pin Docker Compose Neo4j image to `neo4j:5.26.25`.
- Add shared error handling with RFC 7807-style problem responses.
- Add base DTOs and validation annotations.
- Add unit tests for configuration binding and validation.
- Add a lightweight Spring context test.

Acceptance criteria:

- Application starts locally.
- `/actuator/health` works.
- Configuration properties bind and have tests.
- `./mvnw test` passes for the phase.

### Phase 2: Schema Registry

- Implement schema YAML model as Java records.
- Add schema parser and validator.
- Add bootstrap loading from `src/main/resources/schemas`.
- Persist schemas as `SchemaDefinition` nodes.
- Implement schema REST endpoints.
- Add schema activation per knowledge base.
- Add unit tests for schema parsing, validation, immutability rules, and generated validation errors.
- Add integration tests with Testcontainers Neo4j for schema persistence and activation.

Acceptance criteria:

- Predefined schema can be loaded and activated.
- Invalid schema is rejected with clear validation errors.
- Schema versions are immutable.
- `./mvnw test` passes for the phase.

### Phase 3: Document Upload and Metadata

- Implement local filesystem binary storage.
- Implement document upload endpoint.
- Compute SHA-256.
- Persist document metadata in Neo4j.
- Add status transitions and failure tracking.
- Add unit tests for hash calculation, content URI generation, duplicate detection, and status transitions.
- Add integration tests with Testcontainers Neo4j for document metadata persistence and duplicate-hash skip behavior.

Acceptance criteria:

- Uploading a document creates a `DocumentUpload` record with name, size, type, hash, content URI, and upload date.
- Uploading a document with an existing hash in the same knowledge base skips storing a duplicate and returns the existing document.
- Original bytes can be resolved from `contentUri` by service code.
- `./mvnw test` passes for the phase.

### Phase 4: Parsing, Chunking, and Embeddings

- Integrate Apache Tika / LangChain4j Tika parser.
- Implement chunking strategy.
- Integrate OpenAI-compatible embedding model.
- Store `DocumentChunk` nodes with text, metadata, and embedding vectors.
- Create Neo4j vector index for chunks.
- Add unit tests for PDF/TXT/DOCX parser routing, chunking boundaries, and embedding-service orchestration with a fake embedding client.
- Add integration tests with Testcontainers Neo4j for chunk persistence, vector index creation, and vector search.

Acceptance criteria:

- PDF, TXT, and DOCX documents parse to text.
- Chunk records are persisted in order.
- Embeddings are stored and searchable through Neo4j vector index.
- Unit tests cover chunking; integration tests cover Neo4j persistence.
- `./mvnw test` passes for the phase.

### Phase 5: Graph Extraction

- Implement LLM prompt for schema-constrained extraction.
- Require structured JSON output from the model.
- Validate extracted nodes and relationships against active schema.
- Upsert domain graph nodes and relationships with provenance properties.
- Link extracted graph data back to source document/chunks and extraction run.
- Add unit tests for extraction payload validation, schema rule enforcement, provenance mapping, and idempotent upsert planning.
- Add integration tests with Testcontainers Neo4j for writing extracted nodes/relationships and reprocessing without uncontrolled duplicates.

Acceptance criteria:

- Uploaded document can produce graph nodes and relationships.
- Invalid labels, relationship types, and properties are rejected before write.
- Reprocessing the same document does not create uncontrolled duplicates.
- `./mvnw test` passes for the phase.

### Phase 6: Query Generation and Validation

- Implement prompt-to-Cypher generation using active schema.
- Implement generated query DTO with explanation, parameters, and validation result.
- Validate query syntax using Neo4j `EXPLAIN`.
- Enforce read-only query policy.
- Validate labels, relationship types, and properties against active schema.
- Add max rows and timeout controls.
- Add unit tests for read-only policy, unsafe keyword rejection, schema-reference validation, limit injection, and generated-query DTO mapping.
- Add integration tests with Testcontainers Neo4j for `EXPLAIN` validation against real Neo4j.

Acceptance criteria:

- Natural language prompt produces Cypher.
- Invalid or unsafe Cypher is rejected.
- Schema-invalid labels and relationships are rejected.
- Validation can be tested without executing the query.
- Generated Cypher is never executed unless validation succeeds.
- `./mvnw test` passes for the phase.

### Phase 7: Query Execution and Answering

- Execute validated Cypher queries.
- Return tabular JSON result data and metadata.
- Add combined `/ask` endpoint.
- Optionally add vector-assisted retrieval context before Cypher generation.
- Add unit tests for execution request validation, result mapping, and `/ask` orchestration with fake LLM/query services.
- Add integration tests with Testcontainers Neo4j for executing validated read-only queries and rejecting invalid generated queries before execution.

Acceptance criteria:

- Valid read-only Cypher returns results.
- Generated query can be executed through the full flow.
- Errors are returned consistently.
- Generated Cypher is validated before execution in both `/queries/execute` and `/queries/ask`.
- `./mvnw test` passes for the phase.

### Phase 8: Testing and Hardening

- Review and fill test gaps from earlier phases.
- Add broader end-to-end integration tests for the complete synchronous MVP flow.
- Mock LLM clients in tests to keep CI deterministic.
- Add test fixtures for schemas and sample documents.
- Add Docker Compose smoke-test instructions.

Acceptance criteria:

- `./mvnw test` passes.
- Testcontainers tests start Neo4j 5.26.25.
- LLM-dependent code is covered through ports/interfaces and deterministic fakes.

## Suggested First MVP Scope

The first useful implementation should include:

- One default predefined schema in YAML.
- Knowledge base creation and schema activation.
- Document upload with metadata and local binary storage.
- Tika text extraction.
- Chunk creation.
- Embedding generation behind an interface, with fake implementation for tests.
- Neo4j persistence for documents, chunks, and vector index setup.
- Query validation for manually submitted Cypher.

After that foundation is stable, add:

- LLM-generated schema from uploaded document/domain.
- LLM graph extraction.
- LLM prompt-to-Cypher generation.
- Full `/ask` flow.

## Key Design Decisions

- Keep framework/internal labels hardcoded only for infrastructure records such as documents, chunks, schema definitions, and extraction runs.
- Keep domain graph schema external and versioned.
- Use YAML for authoring schemas, Java records for runtime validation, and Neo4j nodes for runtime persistence.
- Store original document binaries outside Neo4j and store only content URI plus hash in metadata.
- Use Neo4j for both graph and vector data to keep the first deployment simple.
- Put all LLM calls behind interfaces so tests can use fakes and the provider can be swapped.
- Validate all model-produced data before writing it to Neo4j or executing it as Cypher.

## Open Questions Before Coding

- Which OpenAI-compatible provider and embedding dimensions should be the default?

Resolved MVP decisions:

- Duplicate document hash uploads in the same knowledge base are skipped and return the existing document.
- First supported document formats are PDF, TXT, and DOCX.
- Document processing is synchronous for MVP.
- Generated Cypher must be validated before execution.
