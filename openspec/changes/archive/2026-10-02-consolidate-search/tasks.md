## 1. Inventory and provider capabilities

- [x] 1.1 Inventory query/advanced-search ownership, persistence/serialization assumptions, exact step-8 exceptions, and residual step-9 support seams in the design; verify every existing search controller, workflow, repository, effect adapter, and assembly dependency has an assigned owner and retirement scope.
- [x] 1.2 Add or extend immutable knowledge-base/schema facts and search-owned ports for admission/existence, optional schema availability, exact stored capture, active resolution, and captured-content parsing; verify ownership-safe missing behavior, availability without parsing, complete immutable snapshots, and absence of foreign records/secrets in contract tests.
- [x] 1.3 Add document-owned scoped batch metadata and bounded filename/content-type selection capabilities; verify duplicate/missing/foreign IDs, ordering, existing selection semantics, candidate bounds, and the 128-ID citation lookup bound in focused capability tests.
- [x] 1.4 Implement mapping-only `bootstrap.integration.search` adapters; verify synchronous reads join caller transactions, mappings retain required fields, and adapters neither access repositories nor add transactions or business policy.

## 2. Query ownership and guardrails

- [x] 2.1 Consolidate query API values/controller, generation, validation, ask orchestration, and deterministic policy under search; replace registry implementation access with public snapshots/contracts and verify existing controller, generation, parsing/schema support, validation, and `QueryAskServiceTest` behavior.
- [x] 2.2 Move configured Neo4j planner/execution mechanics into search-owned ports/adapters and keep driver value mapping inside adapters; verify existing row-limit, parameter, timeout, rejection, effective policy, response-shape, and configured-database tests.
- [x] 2.3 Move query model adapters with search business prompts and consume AI construction through adapter seams; verify existing generation-client tests and metadata-only observation/log behavior without exposing clients or keys in public values.

## 3. Advanced-search policy and retrieval

- [x] 3.1 Consolidate planning, typed graph-plan validation/rendering, and their domain values; replace active-schema implementation types with public immutable values and verify planning, graph validation, renderer, and captured-schema parsing fixtures.
- [x] 3.2 Move graph/text/parent-context retrieval ports and Neo4j adapters under search while preserving shared index-maintenance seams; verify scoped filtering before limits, isolated embedding partitions, lexical legacy readiness, online waits/deadlines, and graph/text retrieval fixtures.
- [x] 3.3 Replace citation metadata and metadata-retriever document repository reads with the new consumer ports; verify one deduplicated scoped batch citation lookup, unchanged fallback/warnings, bounded filter selection, and deadline/branch diagnostics in citation and metadata retrieval tests.
- [x] 3.4 Replace readiness and dense retrieval compatibility-policy calls with AI-owned compatibility and immutable embedding targets; verify readiness without provider requests, empty-corpus conditional checks, text-only admission, compatibility blockers, and dense batch/channel behavior; confirm no production caller remains before deleting `EmbeddingSpacePolicy`.
- [x] 3.5 Consolidate fusion, parent expansion, ranking, citation catalog/evidence assembly, sufficiency/follow-up policy, answer synthesis, and model adapters; verify ranking, answering, expansion, and codec fixtures retain deterministic order, branch-local failures, child text citations, and authoritative graph-parent citations.

## 4. Durable runs and assembly

- [x] 4.1 Consolidate run/attempt/result API/state, services, lifecycle/checkpoints, codecs, repository ports, relational adapters/entities, and recovery/retention under search; verify explicit SQL/entity mappings, JSON/payload versions, historical settings/schema/result fixtures, API/OpenAPI contracts, and existing lifecycle tests.
- [x] 4.2 Replace run admission/list ownership repository reads with public facts; preserve initial readiness, capacity reservation, transactional repeated readiness/profile comparison, exact schema/settings capture, save/dispatch, and reservation release; verify blocker/capacity failures and transactional profile-change behavior with focused tests.
- [x] 4.3 Update necessary component/entity/repository scanning, executor/recovery/scheduler wiring, and shared error mapping; verify fresh startup and application context wiring without introducing document/support dependencies on search implementations.
- [x] 4.4 Verify run claims, cancellation between stages, queued cancellation, late-result suppression, bounded retention, owned polling/results, and stale-run interruption without replay using existing lifecycle and focused PostgreSQL integration tests.

## 5. Enforce boundaries and integrate

- [x] 5.1 Remove exact step-8 document/schema exception sets and the retired compatibility-policy caller freeze; add positive search owner/contract/domain/adapter/integration rules and preserve earlier feature rules; verify `ArchitectureBoundaryTest` rejects foreign persistence access, effectful deterministic policy, feature-to-bootstrap dependencies, and expanded step-9 exceptions.
- [x] 5.2 Freeze residual AI/settings/observability/logging/transaction/shared-index seams by exact origin/target and step-9 assignment, updating paths only as necessary; verify documents retains no dependency on search and compare the residual inventory with the design.
- [x] 5.3 Run `./mvnw test -Pfast`; verify deterministic query, search, contract, codec, lifecycle, and architecture checks pass and resolve migration regressions without changing established behavior.
- [x] 5.4 Run focused `CypherValidationIntegrationTest`, `CypherExecutionIntegrationTest`, `AdvancedSearchTextRetrievalIntegrationTest`, `AdvancedSearchGraphRetrievalIntegrationTest`, `AdvancedSearchRankingExpansionIntegrationTest`, `AdvancedSearchRunIntegrationTest`, and `EndToEndMvpFlowIntegrationTest` with escalated execution for Testcontainers; verify persistence, graph provenance, claim/admission/cancellation/recovery, and canonical application behavior.

## 6. Documentation and completion

- [x] 6.1 Synchronize the architecture portal and affected search workflow pages, `docs/MODULARIZATION_DESIGN.md`, README, AGENTS, and CLAUDE with implemented step-8 facts and remaining step-9 seams; verify links/navigation and overlapping contributor statements agree, including the archived step-7 roadmap link.
- [x] 6.2 Run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` and `./mvnw site`; verify documentation alignment passes and the portal builds with all referenced pages in navigation.
- [x] 6.3 Run `openspec validate consolidate-search --strict` and inspect the final diff; verify all requirements/scenarios and completed task evidence match the implementation, no persisted-format/data migration is introduced, and only identified step-9 exceptions remain.
