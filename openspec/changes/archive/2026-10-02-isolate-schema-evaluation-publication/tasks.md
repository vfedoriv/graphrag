## 1. Establish schema-owned fact contracts

- [x] 1.1 Add internal authoring read interfaces for owned/mutable draft admission, revision/aggregate/projection, canonical decisions/guidance, contributor fingerprints and historical fallback, and readiness/support inputs; verify scoped reads and defensive-copy behavior with focused contract tests and preserve the decision timestamp snapshot fixture.
- [x] 1.2 Add a draft-owned publication-link mutation accepting immutable completion preconditions; verify optimistic version behavior and caller-transaction rollback without exposing draft records or repositories.
- [x] 1.3 Extend registry contracts for validation/parsing, global identity occupancy, associated identity lookup, stored hash/status, and inactive generated registration; verify global-versus-associated behavior, missing schemas, identity conflicts, and inactive registration using focused registry tests.
- [x] 1.4 Provide non-secret knowledge-base/profile facts needed by evaluation and reprocessing through existing or narrowly extended public capabilities and consumer ports; verify profile fields/revisions and association/current-target checks preserve existing behavior and expose no keys.

## 2. Add document preparation and dry-extraction boundaries

- [x] 2.1 Add scoped document inventory/inspection/preparation capability coverage, reusing `DocumentSourceInputs` where sufficient; verify bounded pagination/totals, upload metadata, missing/foreign documents, source hashes, parsing, and existing chunk splitting with focused capability tests.
- [x] 2.2 Add document-owned per-chunk dry extraction with immutable raw and validated observation values; verify unknown labels, invalid relationships, property values, defensive copies, extraction-client selection, and existing profile/validation behavior without embedding or graph effects.
- [x] 2.3 Implement evaluation consumer ports through mapping-only `bootstrap.integration.schemas` adapters; verify complete field mapping, transaction-free adapters, and absence of repositories, provider clients, document implementation values, paths, and policy in the bridges.

## 3. Consolidate evaluation ownership

- [x] 3.1 Move evaluation API mapping, state/enums, repository ports, relational adapters, and configuration/recovery wiring under `schemas.evaluation`; verify entity/table mappings, HTTP/OpenAPI shapes, contract revisions, and historical v1/v2 result readability through existing contract and integration fixtures.
- [x] 3.2 Migrate eligibility and run creation to authoring/document/profile contracts; verify successful-contributor hashes across DOCUMENT/FILE/TEXT, historical fallback, missing analysis, source ownership, revision/aggregate races, canonical decision timestamps, and unchanged snapshot/reuse fingerprints.
- [x] 3.3 Migrate outcome processing and deterministic metric rules to preparation/dry-extraction ports and evaluation-owned observation inputs; verify `SchemaDraftEvaluationServiceTest` and `SchemaDraftEvaluationMetricsCalculatorTest`, including source-check/reuse/client-availability failure precedence, partial outcomes, evidence coordinates, and unchanged advisory fallback.
- [x] 3.4 Replace evaluation creation checkpoints with an evaluation-owned service and relocate claim recovery; verify creation commits before model work, conditional claims, interrupted-run handling, outcome reuse/retry lineage, and no newer owner overwritten by recovery.
- [x] 3.5 Add boundary integration coverage proving successful and failed dry evaluations leave document processing/extraction runs, chunks/embeddings, graph facts/relationships, and draft revision/aggregate unchanged; verify privacy-safe failure and normal-log assertions.

## 4. Consolidate publication ownership

- [x] 4.1 Move publication API/state/persistence and readiness workflow under `schemas.publication`, using authoring and registry contracts plus an evaluation-owned qualification interface; verify existing blocking-reason ordering, guidance/conflict checks, qualifying-run policy, thresholds, and exact revision/hash preconditions.
- [x] 4.2 Split publication intent and completion checkpoints, invoking draft-owned linkage within the completion transaction; verify rollback keeps publication/draft consistent and registry registration remains inactive with no activation or plan side effect.
- [x] 4.3 Migrate publication resume and retrieval to registry contracts; verify retry idempotency, concurrent target identity claims, exact associated identity/content matching after interrupted completion, missing published schema behavior, content drift, and registry active status.

## 5. Complete reprocessing organization and downstream summaries

- [x] 5.1 Move remaining plan/item API, workflows, persistence, and recovery under `schemas.reprocessing`, retaining existing preparation/execution/outcome ports and snapshots; verify controller contracts and `SchemaReprocessingPlanServiceTest`/`SchemaReprocessingRecoveryServiceTest` behavior remain compatible.
- [x] 5.2 Replace plan registry/knowledge-base/profile implementation access with owned immutable fact contracts and preserve activation-to-plan wiring; verify active-schema/hash/profile/embedding/chunker guards, blocker precedence, all-owned classification before selection, preview/creation recomputation, and after-commit scheduling.
- [x] 5.3 Move plan creation/repair checkpoints out of the mixed draft service; verify concurrent destructive-plan exclusion across activation/chunk migration, authoritative item counters/cardinality, plan-local recovery isolation, and the unchanged document-outcome recovery predicate through focused persistence integration tests.
- [x] 5.4 Put evaluation history and reprocessing history/currentness summaries with their owners and add draft-owned bounded summary consumer interfaces; verify `SchemaDraftWorkflowNavigationServiceTest` plus integration pagination/ties, latest/current/retryable classification, filtering before totals, and bounded batch access for draft lists.
- [x] 5.5 Remove downstream persistence and mixed DTO ownership from draft navigation/checkpoints as appropriate, then update required persistence scanning and executor wiring; verify startup/bean resolution and unchanged HTTP JSON/enum/OpenAPI contracts while preserving only exact step-9 support seams.

## 6. Enforce boundaries and validate the migration

- [x] 6.1 Retire exact step-7 document/registry/draft-downstream edges and add positive evaluation/publication/reprocessing, pure-rule, immutable-contract, dry-effect, and mapping-only integration architecture rules; verify `./mvnw test -Pfast -Dtest=ArchitectureBoundaryTest` and confirm retained step-8/9 exceptions have exact pairs and retirement labels.
- [x] 6.2 Run `./mvnw test -Pfast` and focused PostgreSQL/Neo4j integration coverage anchored by `SchemaDraftLifecycleIntegrationTest` and added checkpoint/contract tests; request escalated execution for Testcontainers and verify held-out eligibility, snapshots, claims, publication resume, navigation bounds, and plan recovery pass.
- [x] 6.3 Run the complete credential-free suite with escalated `./mvnw test`, including the canonical `EndToEndMvpFlowIntegrationTest`; verify the full flow and predecessor ownership/behavior tests remain passing without destructive data resets.
- [x] 6.4 Update the architecture and matching draft/evaluation/publication/reprocessing portal pages, `docs/MODULARIZATION_DESIGN.md`, and overlapping README/AGENTS/CLAUDE implementation facts; verify navigation entries for any added portal pages and run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` plus `./mvnw site`.
- [x] 6.5 Validate the completed change with `openspec validate isolate-schema-evaluation-publication --strict`, inspect the final implementation diff for snapshot/SQL/binary compatibility and unintended step-8/9 expansion, and confirm every task has verification evidence before archiving.
