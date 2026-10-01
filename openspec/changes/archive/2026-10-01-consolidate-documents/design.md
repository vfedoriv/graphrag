## Context

See [proposal.md](proposal.md) and [the roadmap](../../../docs/MODULARIZATION_DESIGN.md). Steps 1–3 are implemented and archived. Their public contracts and mapping adapters are the starting point, not scaffolding to replace.

Observed seams:

- `DocumentUploadService` coordinates deduplication, binary storage, storage mutations, cleanup, and run history. `DocumentProcessingService` coordinates stages but constructs several collaborators directly and resolves knowledge-base/profile state through broad services.
- Processing stages and lifecycle components live in `application.processing`; parsing/chunking lives in `document`; document facades already live in `documents.application`.
- `DocumentProcessingRecoveryService` owns stale-run failure and extraction cleanup. `DocumentStorageReconciliationService` owns filesystem journal recovery.
- `GraphWriteService`, `GraphArtifactCleanupService`, document chunk persistence, and relational document/run/storage adapters are document-owned operations outside `documents`.
- Synchronous discovery, draft analysis/evaluation, and search still access document implementation types. `SchemaDraftAnalysisSourceFactory` also reads draft binaries through shared binary storage. Moving all binary infrastructure into documents would incorrectly absorb draft ownership.
- Document embedding persistence and migration preparation still use the frozen `EmbeddingSpacePolicy` bridge. AI compatibility already has an owned application capability.

## Goals / Non-Goals

**Goals:** Put meaningful ownership behind one feature root, isolate deterministic rules from effects, preserve established capabilities, and make every remaining cross-feature exception finite and removable.

**Non-Goals:** Processing or chunking redesign; performance changes; new workers/build modules; moving schema plan/draft state into documents; full AI/knowledge-base/settings migration; schema snapshots (step 5); held-out evaluation preparation (step 7); search retrieval adapters (step 8).

## Decisions

### 1. Relocate by owner, with an explicit package map

Use this organization; subdivide adapters only where real implementations exist:

```text
documents/
  api/                         document/chunking controllers and API mapping
  contracts/                   existing public capabilities and boundary values
  application/management/      upload, replace/delete, storage journals/reconciliation
  application/processing/      orchestration, stages, extraction, run lifecycle/recovery
  application/inspection/      existing stored-embedding inspection
  application/lifecycle/       existing KB document capability
  domain/                      deterministic document/chunk rules and internal values
  ports/                       owned persistence/effect contracts
  adapters/                    relational, graph, parser, binary, model integrations
```

Move document/run/storage persistence records and repository ports with their owner; keep adapter-specific JPA/SDN records in adapters. Internal persistence values are not public capabilities. Preserve table/column names, entity names used in queries, relationship/index identifiers, and serialization annotations explicitly where package changes affect defaults. Update component/entity/repository scanning as needed without broadening other feature access.

Move document-owned graph writes, cleanup, and embedding/chunk persistence together. Leave search-specific retrieval/query adapters in their current area for step 8. Shared database clients/connections, transaction annotations/managers, binary-store primitives used by drafts, provider resolution, settings, and observability stay support-owned; documents adds an owned adapter boundary around its use of those primitives. Do not duplicate or relocate draft binary storage.

Alternative: move only public service classes. Rejected because repositories and effects remain hidden foreign dependencies. Alternative: move all persistence or graph infrastructure into documents. Rejected because it absorbs unrelated feature operations.

### 2. Preserve workflows while isolating effect seams

Keep the same stage order and failure handling. Inject stage/effect collaborators through application assembly rather than constructing an alternative workflow in `DocumentProcessingService`. Concrete deterministic helpers need no interface unless a meaningful boundary exists. Put deterministic chunk hierarchy, metadata/context construction, revision calculations, and option normalization with document domain/application rules according to whether they are pure or orchestrating; keep runtime settings reads outside pure rules.

Keep upload dedup, replacement/deletion cleanup scope and ordering, durable storage intent, processing/extraction checkpoints, startup scheduling order, stale thresholds, bounded recovery, and historical run readability equivalent. No new enclosing transaction may span filesystem/model/graph work. Preserve proxied store-qualified checkpoint calls and recovery after external success followed by relational completion failure.

Alternative: rewrite the orchestrator alongside the relocation. Rejected because it obscures compatibility failures. Extraction prompts/result interpretation belong to documents; reusable AI provider construction remains AI/support-owned, with exact existing dependencies frozen until step 9 if necessary.

### 3. Reuse existing capabilities and AI compatibility

Retain `DocumentReprocessing`, `DocumentProcessingOutcomes`, `DocumentMigrationPreparation`, `KnowledgeBaseDocuments`, and `StoredEmbeddings` with immutable values and existing method semantics. Existing bootstrap integration adapters may update imports but cannot acquire repositories, transactions, or policy.

Switch embedding persistence and migration inspection to `ai.application.EmbeddingCompatibility` and existing non-secret descriptors, preserving space identity normalization, missing tokenizer fallback, incompatibility errors, and empty observations. Documents does not retain its own compatibility algorithm. Keep the legacy policy bridge only for its frozen search callers until step 8; historical identity/tokenizer bridges retire only as their assigned callers migrate in steps 4/8/9.

Schema snapshot access is deferred to step 5. Preserve current extraction schema guards and immutable target restoration now, with exact dependencies to the existing resolver/context recorded. Likewise freeze current knowledge-base/profile resolution and support dependencies; do not introduce new entity-bearing public contracts to make relocation easier.

Alternative: invent a generic cross-feature repository facade. Rejected because it exports state ownership. Alternative: migrate all foreign capabilities here. Rejected because it combines later roadmap slices.

### 4. Enforce the new owner without granting blanket migration exemptions

Extend architecture tests to document API, domain, workflow, ports, adapters, and contracts. Pure rules cannot access effect clients or workflows; application code cannot directly access Neo4j/filesystem/provider clients. New foreign callers use public capabilities. Features cannot depend on bootstrap. Relocate direct-client allowlist entries to owned adapters and remove obsolete legacy write/cleanup exceptions.

Freeze exact existing foreign-origin/target pairs when imports move. Assign synchronous discovery to step 5, draft source/analysis/review access to step 6, evaluation/publication access to step 7, and search to step 8. Assign document-to-schema resolver access to step 5 and remaining AI/knowledge-base/support implementation access to step 9. Inventory actual edges in `ArchitectureBoundaryTest` rather than allowing entire legacy packages. Count/identity assertions must detect both additions and stale unused exceptions. Predecessor reprocessing and knowledge-base/AI bypasses stay forbidden.

Alternative: allow all `service` callers while migration continues. Rejected because it permits new violations invisibly.

## Risks / Trade-offs

- Package moves alter entity discovery or serialization -> explicit mapping/scanning review and relational/snapshot compatibility coverage.
- Constructor changes alter execution order or transactions -> existing stage/lifecycle tests plus focused failure/checkpoint assertions.
- Shared parser/storage users are accidentally absorbed -> move only owned adapters and freeze exact foreign consumers.
- Architecture rules become too permissive -> exact exception pairs with retirement steps and rejected-new-caller coverage.
- Large relocation diff hides behavior changes -> stage migrations by management, processing/rules, then owned adapters; keep algorithms unchanged.

## Migration Plan

1. Migrate management/API and document records/ports with their owned adapters, preserving mapping identifiers and updating exact exception pairs.
2. Migrate processing, parsing/chunking, extraction, run history/recovery, and graph effects; inject collaborators and use AI compatibility directly.
3. Reconnect predecessor capabilities and integration mappings; enforce the document boundary and retire obsolete document bridge/client exceptions.
4. Run the fast suite, focused document/cleanup/reprocessing/knowledge-base/AI integration tests, and the canonical end-to-end flow. Docker/Testcontainers checks require escalated execution.
5. Update the architecture portal, ingestion/chunking pages affected by ownership facts, roadmap status/link, and overlapping README/AGENTS/CLAUDE guidance. Run documentation alignment and site generation.

Deploy as the same application with no persistence migration. Rollback reverts package/wiring changes while preserving data. Historical snapshots and identifiers must remain readable. Step 5 follows in the agreed execution order, removing the frozen document schema-resolution and synchronous discovery exceptions.
