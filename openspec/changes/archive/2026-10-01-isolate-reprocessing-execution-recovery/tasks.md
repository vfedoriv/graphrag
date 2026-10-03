## 1. Boundary contracts

- [x] 1.1 Add schema-owned execution and outcome-reader ports with closed activation/migration targets and immutable values; verify contract tests cover required target fields and defensive collection copying and architecture checks reject entity/client/secret dependencies.
- [x] 1.2 Add document-owned execution and processing-outcome public contracts; verify they have no schema implementation, persisted-plan, runtime-context, or provider-client dependencies.

## 2. Document implementations and integration

- [x] 2.1 Implement the documents execution facade using existing source lookup/hash comparison and overwrite processing; verify missing/replaced sources never invoke processing and completed/non-completed/exception outcomes preserve current categories.
- [x] 2.2 Move immutable chunk-migration input restoration into the document facade while retaining activation option resolution and profile scope; verify facade tests preserve captured profile/revisions/options and restore profile context after both success and failure.
- [x] 2.3 Implement the documents outcome lookup with the existing active-completed/hash/optional-revision/start-time predicate; verify tests cover each mismatch, absent item start, activation without required chunk revision, and a positive recovery match.
- [x] 2.4 Add integration adapters mapping schema/document requests and results without business decisions; verify field-mapping tests cover both target variants and architecture checks prohibit repositories and implementation-service access in adapters.

## 3. Migrate schema callers

- [x] 3.1 Extract the port-only reprocessing item-execution collaborator and route `processItem` through it while retaining schema-owned claims/completion; verify `SchemaReprocessingPlanServiceTest` and focused port tests preserve stale-source, successful, failed, and retryable item outcomes for both reasons.
- [x] 3.2 Replace processing-run repository access in `SchemaReprocessingRecoveryService` with the outcome-reader port; verify recovery tests preserve completed-external-work reconciliation, unmatched-run retry behavior, counter repair, and unrelated malformed-plan isolation.
- [x] 3.3 Remove document-specific runtime input assembly from the schema execution path and retain plan target guards/checkpoint ordering; verify target-change tests still block queued work and no new transaction wraps external processing, including source inspection before target decoding and source-lookup failure propagation.

## 4. Architecture and compatibility verification

- [x] 4.1 Extend `ArchitectureBoundaryTest` for schema ports, the execution collaborator, recovery, document contracts, and integration adapters; freeze remaining preparation edges by explicit source/target and verify a forbidden execution/recovery dependency is rejected without a package-wide exception, including method-origin checks in the legacy orchestrator.
- [x] 4.2 Run `./mvnw test -Pfast`; verify deterministic processing/reprocessing, mapping, profile-scope, recovery, and architecture tests pass and resolve any failures attributable to this change.
- [x] 4.3 Run the relevant cases in `SchemaDraftLifecycleIntegrationTest`, document processing integration coverage, and `EndToEndMvpFlowIntegrationTest` with escalated execution; verify overwrite/run activation, external-success recovery, claims, and schema-activation/chunk-migration exclusion remain intact.

## 5. Documentation and review

- [x] 5.1 Update the architecture/codebase-tour portal pages and overlapping facts in `README.md`, `AGENTS.md`, and `CLAUDE.md`; verify the implemented slice and remaining preparation exceptions agree with `docs/MODULARIZATION_DESIGN.md` without claiming full feature relocation.
- [x] 5.2 Run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` and `./mvnw site`; verify both pass and any new portal page is included in `src/site/site.xml`.
- [x] 5.3 Review the final diff and verify no HTTP contract, SQL migration, persisted snapshot shape, recovery predicate, provider secret handling, or unrelated feature relocation changed; record checks and remaining change-2 preparation exceptions before marking tasks complete.

## Verification record — 2026-10-01

- `./mvnw test -Pfast`: 521 tests passed; no failures, errors, or skips.
- `./mvnw test`: 666 tests passed with escalated Testcontainers execution;
  includes all `SchemaDraftLifecycleIntegrationTest`, `DocumentProcessingIntegrationTest`,
  `SchemaWorkflowRelationalRepositoryIntegrationTest`, and `EndToEndMvpFlowIntegrationTest`
  cases. Real chunk-migration execution adds an overwrite run and leaves exactly
  one active completed run. Recovery reconciles external success before item
  completion for both plan reasons. Existing claim and destructive-plan exclusion
  checks pass.
- `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest`: 6 tests passed.
- `./mvnw site`: built all 24 existing portal pages; no new navigation entry needed.
- `openspec validate isolate-reprocessing-execution-recovery --strict` and
  `git diff --check`: passed.
- Independent review identified source-check ordering/failure parity and a
  same-class preparation-exception gap. The user approved a separate source-check
  operation. A regression test first reproduced `FAILED` instead of `STALE_SOURCE`;
  final tests preserve stale-source precedence over malformed targets and propagate
  lookup failures without completing the claim. Method-origin architecture checks
  and a forbidden-repository fixture prevent execution from reusing preparation
  dependencies. All identified important findings are addressed.
- Diff review confirms no controller/DTO, SQL migration, persisted
  `ChunkMigrationSnapshot` shape, recovery predicate, provider-secret handling,
  or unrelated feature relocation changes. Profile context is restored after
  successful and failing processing. No encompassing transaction or repeated
  source lookup was introduced; the existing source-check race is preserved.

Remaining change-2 preparation exceptions are frozen to
`SchemaReprocessingPlanService` (and its `ChunkMigrationEvaluation` record):
`DocumentUploadRepository`, `DocumentChunkRepository`, `DocumentProcessingRunRepository`,
`DocumentUploadNode`, `DocumentProcessingRunNode`, `DocumentProcessingRunStatus`,
`DocumentProcessingOptionsRegistry`, `DocumentProcessingOptionSet`,
`DocumentFormatDetection`, `ProcessingOptionResolver`, `ProcessingJsonCodec`,
`ChunkingService`, `ChunkingContext`, and `ChunkerRevision`. They support selection,
classification, option resolution, and snapshot preparation only. Execution and
recovery have no document-internal exception, and integration adapters use only
public contract values. The legacy `ChunkingService` snapshot/restore compatibility
overload remains until preparation/consolidation work; new facades do not import
the persisted schema snapshot type.
