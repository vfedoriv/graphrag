## Context

See `proposal.md` for motivation and the architecture delta for required boundaries.

`SchemaDraftEvaluationService` currently reads `DocumentUploadRepository`, then uses upload/storage, parsing, chunking, `GraphExtractionClient`, and extraction validation implementations directly. Its metrics calculator depends on document extraction domain values. `SchemaDraftEvaluationEligibilityService` reads document records and successful contributing source results, including historical document-ID fallback. Runs capture projection, decisions, source hashes, profile identity/revision, prompt/contract revisions, and settings; outcomes are independently persisted and reused by the existing reuse key.

`SchemaDraftPublicationService` reads draft/conflict/decision/aggregate repositories and evaluation runs alongside registry records/repositories/services. It persists an intent before registry creation, resolves an existing matching associated schema on resume, and commits publication completion and draft publication linkage together. Retrieval reports subsequent inactive-schema content drift and registry active status.

`SchemaReprocessingPlanService` and recovery remain in legacy packages while their document preparation/execution/outcome ports already live under `schemas.reprocessing`. Plan services still read registry and knowledge-base state directly. The mixed `SchemaDraftWorkflowCheckpointService` owns evaluation, publication, and plan checkpoints; draft navigation reads downstream repositories directly. Architecture tests record exact remaining document, registry, draft/downstream, and support edges.

`SchemaDraftLifecycleIntegrationTest` anchors held-out eligibility, ISO timestamp decision snapshots, evaluation without graph writes, inactive publication/idempotency/content drift, explicit activation, partial plans/retries, and navigation. Existing service tests anchor metric formulas, source/target guards, all-owned classification, currentness, and recovery. The advisory implementation currently supports deterministic fallback with `COMPLETED_WITHOUT_MODEL_JUDGMENT`; this slice does not add an advisory model adapter.

## Goals / Non-Goals

**Goals:**

- Put operational state and external effects behind their actual owners while preserving observable and persisted behavior.
- Share document extraction mechanics without giving schemas document implementation dependencies or write-capable processing surfaces.
- Use deliberate internal interfaces between schema areas; retain authoring ownership of its state.
- Keep current transaction/checkpoint boundaries and bounded list query behavior measurable during relocation.

**Non-Goals:**

- Change eligibility, metrics, advisory behavior, chunking algorithms, snapshot/reuse identity, extraction validation, profile resolution, or publication policy.
- Strengthen source-race semantics, impose new model/profile revision enforcement, redesign recovery predicates, or introduce cross-store atomicity.
- Consolidate search, relocate all AI/settings/observability support, introduce build modules, or create remote execution contracts.
- Perform SQL/binary migrations, rewrite historical JSON, or create empty package layers solely for symmetry.

## Decisions

### 1. Consolidate schema workflow ownership with narrow internal seams

Move evaluation controllers/API values, orchestration, eligibility, contract mapping, metrics rules, run/outcome records, repositories, relational adapters, and stale-claim recovery under `schemas.evaluation`. Move publication equivalents under `schemas.publication`, and complete plan/item API, workflow, state, persistence, and recovery organization under `schemas.reprocessing`. Retain existing execution/preparation ports and integration adapters rather than replacing established boundaries.

Expose authoring facts through purpose-specific internal schema interfaces: owned/mutable draft admission, current revision/aggregate/projection, canonical decisions/guidance, successful contributor fingerprints plus historical fallback IDs, conflict/guidance readiness inputs, and support-risk inputs. Only expose a fact where a downstream consumer needs it; do not introduce one broad draft service or return authoring records/repositories. Draft publication linkage remains a draft-owned mutation invoked from publication's completion transaction using immutable preconditions and preserving optimistic version behavior.

Registry parser/validator implementation remains registry-owned. Evaluation obtains projection parsing through a registry contract rather than depending directly on `SchemaParser`. Reuse complete `schemas.contracts` values where appropriate. Internal schema contracts need not become externally exposed HTTP APIs or exported cross-feature services.

Alternative: move every service but continue reading authoring/registry repositories because all are within schemas. Rejected because that would preserve the exact seams the earlier slices deliberately froze. Alternative: one interface per helper. Rejected where ordinary internal pure collaboration suffices.

### 2. Separate held-out document preparation from per-chunk dry extraction

Add evaluation-owned ports for scoped document inventory/inspection/preparation and dry extraction. Implement them in mapping-only `bootstrap.integration.schemas` adapters backed by document public capabilities. Reuse `DocumentSourceInputs` metadata/content/parsing where sufficient; extend or add a purpose-specific public inventory/preparation capability for pagination (including upload timestamp), existing chunk splitting, and consistent missing/foreign behavior. Keep local storage references and document records inside documents.

Documents owns source loading, parsing, chunk splitting, extraction-client selection, profile context, and validation. Schemas owns the run/outcome loop and invokes dry extraction per chunk, preserving existing sequential execution and observation scope. Pass complete immutable schema inputs and non-secret profile identity facts; provider construction and keys stay AI-owned. Return deeply immutable raw and validated node/relationship observations retaining property values, keys, labels, endpoints, and ordering sufficient for the existing metric calculator and evidence coordinates. Map these into evaluation-owned metric inputs; do not expose `documents.domain.extraction` values or calculate schema evaluation metrics in the bridge.

Preserve the current order: owned source/hash check, reusable-outcome lookup, AI-client availability classification, binary load/parse/chunk, extraction/validation, calculation, outcome checkpoint. Missing/replaced sources retain stale classification; provider absence retains `AI_CLIENT_UNAVAILABLE`; preparation/model failures retain privacy-safe retryable failure handling. The current source metadata check and concurrent replacement behavior are behavior anchors, not an opportunity for a separate race-policy change. Preserve current live chunking and profile execution behavior rather than adding persisted settings or silently pinning new inputs.

The dry implementation must have no dependency on graph writers, embedding generation/persistence, processing-run creation, or document state mutation. Source reads, model calls, and observations are permitted effects; “dry” means no document-processing or graph persistence. Focused tests verify the forbidden effects, including failure paths.

Alternative: invoke `DocumentProcessingService` with a dry flag. Rejected because the normal workflow has cleanup, run, embedding, and graph effects. Alternative: return only sanitized extraction. Rejected because unknown/dropped observations are needed for existing metrics. Alternative: return all document extraction results at once. Rejected because per-chunk calls retain the existing processing order and avoid accumulating a new whole-document result payload.

### 3. Registry owns lookup/validation/registration; publication owns intent and readiness

Add narrow registry contract methods for validation/parsing, global identity occupancy, knowledge-base-associated identity lookup, stored schema/hash/status retrieval, and inactive generated registration. Reuse `StoredSchemaSnapshots` and active-resolution values where sufficient. Return immutable results; keep exact global-versus-associated and global active-status semantics. Publication's consumer port maps these operations without owning schema identity rules.

Publication retains conflict/guidance/evaluation readiness decisions, expected revision/hash checks, target-identity claim, and durable publication intent. Registry validates and creates/associates an ordinary inactive schema under its existing transaction. Completion updates the publication and invokes draft-owned publication linkage in the existing relational completion transaction. Resume verifies aggregate and content hash, then resolves the same associated name/version/content or registers it if absent. Preserve conflict precedence and uniqueness handling, including concurrent identity claims and content mismatch; retrieval continues to detect content drift or missing published schemas.

Expose evaluation-owned qualification facts for publication readiness rather than evaluation records. Preserve the existing qualifying-run selection/status/threshold policy; do not opportunistically alter its revision/hash checks. Reprocessing uses immutable stored schema/association and non-secret profile/embedding facts through schemas-owned ports backed by registry, knowledge-base, and AI public capabilities. Extend existing fact surfaces only when they lack required fields. Keep registry activation's lock and after-commit plan trigger through a deliberate schema-internal activation-to-reprocessing interface.

Alternative: wrap registry records in a public result or make publication one encompassing transaction. Rejected because records leak ownership and the current durable intent/resume protocol must remain compatible. “Atomic and idempotent” publication in the existing spec is implemented through transaction handling and resumable reconciliation; this change does not claim that intent, registration, and completion have always been one commit.

### 4. Split checkpoints and move downstream summaries to their owners

Replace the mixed checkpoint service with evaluation-owned run/outcome creation, publication-owned intent/completion, and reprocessing-owned creation/repair checkpoints. Preserve annotation/proxy entry points and store-specific transactions, including destructive-plan exclusion and publication-plus-draft completion. Mapping adapters add no transactions. Synchronous metadata/fact reads participate in caller transactions; binary/model work remains outside an enclosing relational workflow transaction.

Evaluation owns its history/detail summaries; reprocessing owns plan history/currentness summaries. Draft navigation consumes purpose-specific batch summary ports returning immutable latest/current/retryable facts and resource references. Keep stable ordering and pagination totals; avoid per-draft round trips or calling detailed workflow methods from list mapping. Move mixed DTO members only as necessary to establish ownership while preserving JSON, enum values, validation, OpenAPI schemas, and paths.

Do not use this slice to relocate unrelated navigation support reads: any genuinely step-9 support/assembly edge remains exactly recorded. Downstream evaluation/reprocessing persistence access and mixed checkpoints are addressed here. Required persistence scanning/executor wiring changes are included; general assembly cleanup remains deferred.

Alternative: leave the mixed checkpoint service or return repositories through summary ports. Rejected because either retains misplaced ownership. Alternative: rebuild navigation by composing one detail request per row. Rejected because it changes query bounds.

### 5. Retire precise exceptions with executable evidence

Remove step-7 entries in `FROZEN_DOCUMENT_EDGES` and `FROZEN_SCHEMA_BRIDGE_EDGES`, plus the migrated evaluation/publication/reprocessing entries in `FROZEN_DRAFT_LATER_EDGES` and `FROZEN_DRAFT_DOWNSTREAM_EDGES`. Replace them with positive owner/contract/domain/integration rules. Retain only explicitly inventoried step-8 and step-9 edges; package relocation may update an exact originating path but must not broaden a permitted target set or turn internal repositories into public contracts.

Keep earlier document preparation/execution/recovery, knowledge-base cleanup, AI compatibility, registry/discovery, and authoring rules intact. Enforce immutable secret-free values, pure metrics rules, forbidden dry-extraction writes, mapping-only integration, and no feature-to-bootstrap dependency. Check JPA scanning and Spring proxy behavior after moves rather than assuming package relocation is harmless.

## Risks / Trade-offs

- [Relocation changes entity names, repository scanning, or serialized type assumptions] -> Preserve explicit SQL/table/entity mappings and contract revisions; verify historical evaluation/publication/plan reads and fresh application startup.
- [Boundary mapping loses raw invalid observations or changes metric counts] -> Preserve raw and validated shapes with defensive copies; use existing metric fixtures and add mixed unknown-label/invalid-relationship/property examples.
- [Preparation changes failure precedence, reuse, or source races] -> Assert stale, missing/foreign, reused, unavailable-client, and parse-failure paths before and after migration.
- [Checkpoint splitting changes commit/proxy ordering] -> Verify rollback participation, concurrent claims, publication resume after registration, and outcome recovery independently of model effects.
- [Navigation becomes unbounded or circular] -> Place summary production with workflow owners; test batch invocation/query bounds, pagination, currentness, and retry lineage.
- [A pure refactor drifts into advisory/profile/recovery fixes] -> Preserve observed behavior, including deterministic advisory fallback and the existing recovery predicate; plan any functional discrepancy separately.

## Migration Plan

1. Add immutable authoring/registry/evaluation/knowledge-base fact surfaces and document preparation/dry-extraction capabilities with mapping-only adapters and focused contract tests.
2. Move evaluation, publication, and remaining reprocessing implementations/persistence/API ownership while preserving storage and serialized identities; split checkpoints and replace direct foreign/internal-area persistence reads.
3. Replace downstream navigation access with bounded summaries, update required persistence scanning and assembly, and retire the exact step-7 exceptions.
4. Run fast deterministic tests and focused PostgreSQL/Neo4j integration coverage, including publication resume, evaluation/no graph effects, plan recovery/claims, navigation, and canonical end-to-end behavior.
5. Synchronize portal architecture/workflow pages, the roadmap, README, AGENTS, and CLAUDE; run documentation alignment, site generation, and strict change validation.

Deploy as the same application without SQL or binary migration. Rollback reverts code/wiring while retaining compatible relational histories, snapshots, registry schemas, document content, and graph artifacts. No destructive store reset is needed.
