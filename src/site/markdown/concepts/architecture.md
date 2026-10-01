# System architecture

GraphRAG is an API-first Spring Boot application. Thin REST controllers delegate to workflow services, which use repository ports backed by PostgreSQL adapters or graph-only Neo4j adapters. Model-produced graph payloads and Cypher are never trusted without validation.

## Container and service flow

```mermaid
flowchart LR
    UI[graphrag-ui or API client] -->|HTTP /api/v1| C[REST controllers]
    C --> S[Application services]
    S --> RP[Repository ports]
    RP --> PG[(PostgreSQL 17\noperational state)]
    RP --> NEO[(Neo4j 5\nchunks, vectors, facts, provenance)]
    S --> FS[(Filesystem\ndocument and draft binaries)]
    S --> MF[AiRuntimeModelFactory]
    MF --> AI[OpenAI-compatible chat and embeddings]
    S -. spans and metrics .-> OTEL[OpenTelemetry / Micrometer]
    OTEL -. optional OTLP .-> LF[Langfuse stack]
```

The default profile can boot without model beans. AI-backed services resolve the active knowledge-base profile at operation time and obtain revision-scoped clients from `AiRuntimeModelFactory`.

## Layer boundaries

1. **Controllers and DTOs** own HTTP mapping, validation, response status, and metadata-only request logging.
2. **Services** own lifecycle rules, admission, orchestration, cleanup, and policy snapshots.
3. **Repository ports** express persistence needs without making domain workflows depend on adapter details.
4. **PostgreSQL adapters** persist all operational aggregates in Flyway's `app` schema.
5. **Neo4j adapters/services** persist retrieval chunks and graph-native facts/evidence/provenance only.
6. **Filesystem storage** owns binary bytes; PostgreSQL owns their metadata and state.

Cross-store operations are explicit workflows rather than distributed transactions. Services record durable state, perform bounded work, and apply cleanup/recovery rules when later steps fail.

## Document ownership

Document consolidation (roadmap step 4) is implemented. `documents.api` owns
`DocumentController`, `ChunkingStateController`, and API models.
`documents.application.management` owns upload, replacement, deletion, storage
mutation/reconciliation, and chunking-state workflows.
`documents.application.processing` owns parsing/chunking orchestration, processing
stages, extraction validation, run lifecycles, recovery, and public capability
facades. `bootstrap.DocumentsProcessingConfiguration` injects assembled stages
into `DocumentProcessingService`.

`documents.domain` contains operational records and deterministic parsing,
chunking, hierarchy, contextual-text, metadata, revision, and extraction values.
Pure rules have no persistence annotations, effect clients, or adapter/workflow
dependencies. `documents.ports` expresses relational, binary, chunk, graph-write,
cleanup, and model effects. `documents.adapters` owns their relational, graph,
binary, parsing, chunking, and model integrations. The mapped SDN
`DocumentChunkEntity` is separate from the plain `DocumentChunkNode`; labels,
properties, queries, IDs, and optimistic-version behavior are preserved.
The binary adapter delegates shared storage primitives; draft storage stays shared.

Document processing and migration preparation use AI-owned
`EmbeddingCompatibility` and immutable non-secret `EmbeddingTarget` values.
Relational checkpoints remain separate from model, filesystem, and graph effects;
scoped cleanup, persisted snapshots, stale-run recovery, and the absence of an
enclosing cross-store transaction are unchanged.

`ArchitectureBoundaryTest` freezes exact class-to-class transitional edges with
retirement steps: registry/discovery and active-schema resolution (5), draft
sources/analysis (6), dry evaluation/publication and schema-owned migration
snapshots (7), search readers (8), and settings/AI/support assembly (9).
These allowances neither reopen completed reprocessing/KB/AI boundaries nor
permit additional foreign document callers. Step 5 remains pending.

## Reprocessing execution and recovery boundary

Schemas owns reprocessing plan state, conditional claims, target guards, item
completion, retry decisions, and counter repair. `ReprocessingItemExecution` uses
the schemas-owned `ReprocessingDocumentExecutor` port; recovery uses
`ReprocessingProcessingOutcomeReader`. The ports carry immutable values with
distinct schema-activation and chunk-migration targets, without persistence
entities, runtime chunking contexts, model clients, or provider keys.

`bootstrap.integration.reprocessing` maps these ports to the documents-owned
`DocumentReprocessing` and `DocumentProcessingOutcomes` capabilities. The
documents facades check source identity, restore saved migration inputs, scope
the processing profile, and inspect owned processing runs. They delegate to
document-owned `DocumentProcessingService` and repository ports.
The adapters add no transactions or business decisions, and features do not
depend on their implementation.

The source-check operation runs after the item claim and before target decoding.
A missing/replaced source completes as non-retryable `STALE_SOURCE`; a source
lookup failure propagates, leaving the claim for recovery. Execution follows a
matching check without a repeated lookup, preserving the existing race semantics.

Recovery retains the historical predicate: an active completed run must match
the item's source hash, the chunker revision when required for migration, and a
start time no earlier than the item's start when present. Activation does not
require a chunker revision or additional schema/profile matching. Relational
claims and checkpoints remain separate from model, filesystem, and graph work.

Preparation and document-specific target inspection use the schemas-owned
`ReprocessingDocumentPreparation` port, mapped to `DocumentMigrationPreparation`
and `DocumentMigrationPreparationFacade`. Documents owns ownership-safe source
summaries, parser/options resolution, chunk presence and completed-run
classification, and chunker/embedding target inspection. Requests capture profile
identity/revision and non-secret embedding/tokenizer inputs; runtime objects and
provider keys never cross these contracts.

Schemas retains activation/chunk selection policy, schema target checks, blocker
priority, preview counts/pagination, durable snapshot assembly, retry lineage,
and destructive-plan exclusion. Preview and creation share read-only preparation;
creation recomputes facts and rejects stale revisions or blockers before plan
persistence. Classification still covers all owned documents before selection,
including explicit-ID previews. Target inspection preserves currentness semantics.

`ArchitectureBoundaryTest` now rejects document internals throughout preparation,
execution, retry, recovery, and currentness without preparation exceptions.
Integration adapters only map public immutable values. Synchronous document reads
participate in existing caller transactions; plan checkpoints and scheduling after
commit remain schema-owned. HTTP contracts, SQL, canonical snapshot JSON and
fingerprints, processing algorithms, and the recovery predicate remain unchanged.
AI compatibility uses AI-owned rules and stored-observation ports; document ownership is consolidated as described above.

## Knowledge-base lifecycle and AI state boundaries

`KnowledgeBaseService` owns existence checks, non-empty deletion admission, and
count → cleanup → relational-delete ordering. `OwnedDocumentState` and
`KnowledgeBaseArtifactCleanup` are knowledge-base-owned ports. Their mapping
adapter uses `KnowledgeBaseDocuments`, backed by `KnowledgeBaseDocumentsFacade`.
Documents retains owned-record inspection, graph/evidence/chunk cleanup, and
lexical-index removal. Non-empty deletion changes no records, binaries, or graph
artifacts. Cleanup failure prevents relational deletion; earlier external effects
retain existing partial-failure semantics, without cross-store rollback.

AI owns `EmbeddingTarget`, `StoredEmbeddingObservation`, `EmbeddingTokenizer`,
and `EmbeddingCompatibilityRule`. Historical normalized endpoint/model/dimension
hashes remain unchanged; resolved tokenizer is a separate compatibility condition.
Blank stored tokenizer falls back from the stored model. Missing space identity
is incompatible and is never backfilled. `EmbeddingCompatibility` reads
`StoredEmbeddingInformation` through the document `StoredEmbeddings` capability
and `StoredEmbeddingsFacade`, keeping the existing embedded-child chunk scope.
Contracts contain raw immutable facts without vectors, entities, or secrets.

AI profile update/delete reads `ProfileAssignments` through the knowledge-base
`AiProfileAssignments` capability and facade. `RelationalAiProfileRepository`
persists profiles only. Assignment and shared-profile compatibility checks run
before fields, revisions, defaults, associations, persistence, or client
invalidation change. Counts and assignments participate in caller transactions;
integration adapters under `bootstrap.integration.ai` and
`bootstrap.integration.knowledgebase` only map values and add no transactions.

`EmbeddingSpacePolicy` remains a repository-free delegating bridge with exactly
`AdvancedSearchReadinessService` and `DenseTextRetriever` as callers (search step 8).
`EmbeddingSpaceIdentity` and `EmbeddingSpace` retain historical utility/value
entry points for search and index support, retiring in steps 8/9. The immutable
`TokenizerId` value in `documents.domain.chunking` remains a frozen dependency
of legacy profile and knowledge-base services until support consolidation in
step 9. No allowance permits foreign state reads. Architecture tests enforce
pure rules/contracts, public-capability mapping, feature-to-bootstrap isolation,
and exact bridge callers alongside all predecessor reprocessing guards.
Document consolidation (step 4) is implemented; schema registry/discovery
boundaries (step 5) remain pending.

## Major flows

- Ingestion: upload → SHA-256 dedup → filesystem binary + PostgreSQL metadata → parse → chunk → embed → Neo4j chunks/vector index → schema-constrained extraction → validated graph write.
- Safe Cypher: prompt/manual Cypher → schema and keyword checks → Neo4j `EXPLAIN` → limit policy → read-only execution.
- Advanced search: readiness/admission → durable PostgreSQL run → planned dense/lexical/metadata/graph branches → fusion/reranking → sufficiency/follow-up → cited synthesis → atomic result publication.
- Schema drafts: durable sources and revisions → bounded analysis → review decisions/conflicts → held-out evaluation → inactive publication → explicit activation → optional reprocessing plan.

## Implementation source map

| Area | Entry points | Core implementation |
|---|---|---|
| Schema registry and discovery | `controller/SchemaController.java` | `service/SchemaRegistryService.java`, `service/SchemaDiscoveryService.java`, `schema/SchemaParser.java`, `schema/SchemaValidator.java` |
| Knowledge bases and profiles | `controller/KnowledgeBaseController.java`, `controller/AiProfileController.java` | `service/AiProfileService.java`, `service/AiRuntimeModelFactory.java` |
| Documents and chunks | `documents/api/DocumentController.java`, `documents/api/ChunkingStateController.java` | `documents/application/management/DocumentUploadService.java`, `documents/application/processing/DocumentProcessingService.java`, `documents/application/processing/ChunkingService.java` |
| Reprocessing preparation, execution, and recovery | `controller/SchemaReprocessingPlanController.java` | `service/SchemaReprocessingPlanService.java`, `service/SchemaReprocessingRecoveryService.java`, `schemas/reprocessing/ports`, `schemas/reprocessing/application`, `documents/contracts`, `documents/application/processing`, `bootstrap/integration/reprocessing` |
| Graph extraction | document processing endpoint | `documents/application/processing/GraphExtractionService.java`, `documents/adapters/graph/GraphWriteService.java` |
| Cypher | `controller/QueryController.java` | `service/CypherGenerationService.java`, `service/CypherValidationService.java`, `service/CypherExecutionService.java` |
| Advanced search | `controller/AdvancedSearchRunController.java` | `service/AdvancedSearchRunService.java`, `service/DefaultAdvancedSearchRunProcessor.java` |
| Runtime settings | `controller/RuntimeSettingsController.java` | `service/RuntimeSettingsService.java` |
| Observability | all AI workflows | `observability/AiObservationService.java` |

All paths above are relative to `src/main/java/io/github/vfedoriv/graphrag/`. For exhaustive routes and DTOs, use [Swagger/OpenAPI](../reference/api.md).
