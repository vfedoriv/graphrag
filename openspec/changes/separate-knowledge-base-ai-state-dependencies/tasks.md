## 1. Document state and cleanup capabilities

- [ ] 1.1 Add knowledge-base-owned document-count and scoped-cleanup ports plus immutable document provider contracts; verify contract dependencies exclude persistence types, implementation services, clients, and secrets.
- [ ] 1.2 Implement document count/cleanup facades using existing repository and cleanup operations, then map them under `bootstrap.integration.knowledgebase`; verify owned count/scope mapping, unchanged cleanup results/effects, and propagation of provider failures with focused facade/adapter tests.
- [ ] 1.3 Switch `KnowledgeBaseService.delete` to the ports and remove document repository/cleanup dependencies and bypass constructors; verify missing-KB handling, non-empty count conflict, no effects after rejection, count-before-cleanup-before-delete ordering, and no relational deletion after cleanup failure.

## 2. Deterministic AI compatibility

- [ ] 2.1 Add immutable AI target/stored-observation values and the deterministic compatibility rule under AI ownership; verify historical endpoint/model/dimension identity fixtures, equal-dimension different-provider/model conflicts, tokenizer changes, blank/missing tokenizer fallback for known/unknown models, missing space identity rejection, and empty stored state.
- [ ] 2.2 Add the AI-owned stored-embedding information port, document inspection capability/facade, and `bootstrap.integration.ai` mapper; verify the existing embedded-chunk query scope, empty/null result handling, raw stored-field mapping, and no entity/vector/secret leakage.
- [ ] 2.3 Add the AI compatibility application capability and delegate existing `EmbeddingSpacePolicy` entry points to it; migrate knowledge-base assignment checks to non-secret compatibility values and remove chunk-repository compatibility constructors. Verify existing `KnowledgeBaseServiceAiProfileTest` and `AiProfileServiceTest` compatibility cases, plus unchanged single/multiple-KB rejection and `hasEmbeddedChunks` behavior.
- [ ] 2.4 Retain only necessary compatibility bridges for processing, preparation, and search callers; verify focused caller regressions and that the bridge delegates without repository access or duplicated compatibility logic.

## 3. Knowledge-base profile assignment ownership

- [ ] 3.1 Add the AI-owned assignment lookup port and knowledge-base public capability using existing assignment presence/ID operations; wire the mapping adapter and verify ID/order/empty semantics, presence results, and provider-failure propagation with focused tests.
- [ ] 3.2 Switch `AiProfileService.update` and `delete` admission to the assignment port, then remove assignment methods and the knowledge-base repository dependency from AI profile persistence; verify assigned/default deletion rejection and incompatible shared-profile update before fields, revision, defaults, persistence, or client invalidation change.
- [ ] 3.3 Update service construction and wiring tests so required production capabilities cannot default to null or skip compatibility/assignment checks; verify application startup and explicit deterministic test wiring without a feature-to-bootstrap source dependency.

## 4. Architecture enforcement

- [ ] 4.1 Extend `ArchitectureBoundaryTest` to reject document implementation access from migrated knowledge-base/AI paths and knowledge-base repository/entity access from AI profile persistence; verify negative fixtures fail for both prohibited state accesses and that normal production dependencies pass.
- [ ] 4.2 Enforce immutable contract, pure-rule, integration-adapter, and feature-to-assembly boundaries; verify negative fixtures reject provider-contract bypasses, direct adapter persistence access, and feature dependencies on bootstrap while preserving predecessor reprocessing guards.
- [ ] 4.3 Freeze any necessary unmigrated compatibility bridge callers/dependencies by exact class/path and record their later retirement slice; verify a new caller/dependency cannot inherit an exception and no exception permits the state accesses removed in this change.

## 5. Transaction and workflow regressions

- [ ] 5.1 Run `./mvnw test -Pfast` and verify all deterministic tests pass, including metadata-only logging and reprocessing preparation/target compatibility regressions.
- [ ] 5.2 Extend relevant container-backed profile/knowledge-base integration coverage for compatible assignment commits, incompatible assignment/shared-profile rollback, optimistic-version conflicts, assigned-profile deletion rejection, and caller-transaction participation of count/assignment reads; run focused methods in `KnowledgeBaseControllerIntegrationTest` and `SettingsAndAiProfileRelationalIntegrationTest` with escalated execution and verify unchanged persisted fields/associations on rejection.
- [ ] 5.3 Verify scoped cleanup preserves other knowledge bases and supported shared facts, non-empty deletion changes neither records nor binaries/artifacts, empty deletion removes existing associations/artifacts, and cleanup failure prevents relational deletion. Run relevant lifecycle/cleanup integration tests with escalated execution and report external partial-effect semantics without claiming cross-store rollback.
- [ ] 5.4 Run the predecessor reprocessing caller-transaction/recovery test and `EndToEndMvpFlowIntegrationTest` with escalated execution; verify historical targets remain usable and document processing/query flow still succeeds.

## 6. Documentation and completion

- [ ] 6.1 Update the architecture and codebase-tour portal pages plus overlapping facts in `README.md`, `AGENTS.md`, and `CLAUDE.md`; correct `docs/MODULARIZATION_DESIGN.md` status/archive links and describe completed step 3 with steps 4/5 still pending. Verify documentation describes actual implemented ownership and names remaining transitional bridges.
- [ ] 6.2 Run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest`, `./mvnw site`, `openspec validate separate-knowledge-base-ai-state-dependencies --strict`, and `git diff --check`; verify all pass and record the focused deterministic/container verification results for review.
