## 1. Management ownership and persistence

- [x] 1.1 Move document upload/run/storage internal records and repository ports into document-owned packages, preserving mapping names and serialization; verify `./mvnw test -Pfast` compilation and existing snapshot/contract tests.
- [x] 1.2 Move relational document upload, processing/extraction run, and storage-mutation adapters/entities/repositories to document adapters; update scanning and verify `DocumentWorkflowRelationalRepositoryIntegrationTest` under escalated execution preserves tables and run history.
- [x] 1.3 Move upload/replace/delete management and storage mutation/reconciliation workflows into `documents.application.management`; verify `DocumentUploadServiceTest` and `DocumentStorageReconciliationServiceTest` preserve dedup, failure journals, and cleanup ordering.
- [x] 1.4 Add the document-owned binary-effect adapter around existing shared storage primitives without relocating draft storage; verify upload/reconciliation tests and draft-storage consumers compile against unchanged support behavior.
- [x] 1.5 Move document and chunking API entry points/mapping into `documents.api`; verify `DocumentChunkOpenApiContractTest` and escalated `DocumentControllerIntegrationTest` preserve routes, response fields, and error behavior.

## 2. Processing, rules, and graph effects

- [x] 2.1 Move processing options/codec, option resolution, parsing/chunk preparation stages, and deterministic hierarchy/context/metadata/revision logic to document-owned application/domain packages; verify existing option, parser, chunking, hierarchy, contextual-text, and revision tests.
- [x] 2.2 Move parser integrations and document chunking orchestration behind owned adapter/support seams; verify routed-parser and recursive chunking tests preserve parser routing, token counts, effective revisions, and saved settings restoration.
- [x] 2.3 Move document chunk/embedding persistence and document-owned graph-write/cleanup implementations into adapters with owned effect contracts; verify chunk persistence/topology and `GraphArtifactCleanupServiceTest` preserve scoping and idempotence.
- [x] 2.4 Move extraction orchestration and document-specific prompt/result/validation collaborators into document ownership, preserving the frozen schema resolver access; verify graph extraction stage/cleanup tests and expected-target behavior.
- [x] 2.5 Move processing/extraction lifecycle, history, and stale-run recovery components; verify `DocumentRunLifecycleTest` and `DocumentProcessingRecoveryServiceTest` preserve checkpoint calls, stale bounds, terminal statuses, and recovery cleanup.
- [x] 2.6 Move `DocumentProcessingService` and inject its stage/effect collaborators through assembly; verify `DocumentProcessingServiceTest` and stage tests preserve ordering and failure short-circuiting without a cross-store enclosing transaction.

## 3. Public capabilities and compatibility

- [x] 3.1 Reconnect existing document reprocessing, outcome, preparation, lifecycle, and stored-embedding facades and bootstrap mappings to relocated internals; verify their focused facade/adapter tests and predecessor ownership rules.
- [x] 3.2 Replace document embedding/preparation use of `EmbeddingSpacePolicy` with AI `EmbeddingCompatibility` and immutable non-secret targets; verify incompatibility, missing-tokenizer fallback, historical space IDs, and empty-observation behavior in existing compatibility/preparation/stage tests.
- [x] 3.3 Remove migrated document callers from the frozen compatibility-bridge allowlist and preserve only remaining search callers; verify architecture assertions detect both unexpected callers and stale exceptions.

## 4. Boundary enforcement and regression verification

- [x] 4.1 Extend `ArchitectureBoundaryTest` to document API/domain/workflow/ports/adapters/contracts and move graph-client allowances to owned adapters; verify forbidden effect clients in pure rules/workflows and feature-to-bootstrap dependencies are rejected.
- [x] 4.2 Freeze exact remaining foreign document callers and document-to-schema/KB/AI/support dependencies with retirement steps 5–9, including relocated draft/search imports; verify exact-set checks reject additions and preserve all completed reprocessing/KB/AI isolation rules.
- [x] 4.3 Run `./mvnw test -Pfast`; verify the complete deterministic suite passes after the relocation and public-contract wiring.
- [x] 4.4 Under escalated execution, run focused upload/controller/processing/chunk repository and relational workflow integration tests plus extraction-cleanup, reprocessing, KB lifecycle, and profile compatibility coverage; verify existing persisted snapshots, checkpoint/recovery behavior, and cleanup scoping.
- [x] 4.5 Under escalated execution, run `./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest`; verify the canonical flow remains compatible and graph-only versus relational ownership is preserved.

## 5. Documentation and readiness

- [x] 5.1 Update implemented document ownership/navigation in the architecture and relevant ingestion/chunking portal pages, `docs/MODULARIZATION_DESIGN.md`, and overlapping README/AGENTS/CLAUDE facts; verify only step 4 is marked implemented and step-5 exceptions remain documented.
- [x] 5.2 Keep `src/site/site.xml` aligned for any added portal page and run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` plus `./mvnw site`; verify documentation alignment and portal generation succeed.
- [x] 5.3 Validate the completed change and check step 5's input/resolver mappings against the final document packages; verify `openspec validate consolidate-documents --strict` and a documented coherent handoff to the next change.
