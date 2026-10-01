# Implementation verification

Verified on 2026-10-01.

- `./mvnw test -Pfast`: 533 tests passed, no failures/errors/skips.
- `./mvnw test -Dtest=SchemaDraftLifecycleIntegrationTest#evaluatesPublishesActivatesAndRetriesAPartialReprocessingPlan+chunkPreparationParticipatesInCallerTransactionAndRecoveryUsesSavedTargets,EndToEndMvpFlowIntegrationTest`: 3 container-backed tests passed with escalated execution.
- `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest`: 6 tests passed.
- `./mvnw site`: passed; all 24 existing portal documents rendered. No new portal page was introduced.
- `openspec validate isolate-document-migration-preparation --strict`: passed.
- `git diff --check`: passed.
- Independent implementation review: no remaining Critical or Important findings. A temporary provider-contract bypass in the architecture guard was removed and a negative fixture verifies rejection.

## Preserved contracts and behavior

- Consumer preparation ports map to document capabilities using immutable summaries, non-secret profile descriptors, and typed chunk/document targets. Architecture tests forbid repositories, entities, runtime chunkers, clients, provider contracts, and implementation services in schema reprocessing paths; integration adapters only map public contracts.
- Activation choice validation, explicit-ID ownership/not-found behavior, distinct-ID order, uploaded-time ordering, all-owned classification before selection, outdated/no-chunk inclusion, blocker priority, and bounded preview pagination retain their existing policy.
- Classification retains both existing chunk reads and the active-completed/status/source-hash/effective-revision predicates. No query optimization or repository query was introduced.
- Snapshot comparison verifies every canonical chunk/document field, effective merged options, fingerprint, and restored chunker settings/revision/tokenizer revision against the legacy construction. Persisted snapshot types and JSON formats are unchanged.
- Preview is read-only; creation recomputes facts, rejects stale/changed revisions and blockers, and does not persist partial plans/items on failure. Retry retains successful items and unresolved-item lineage. History currentness rejects changed schema identity/hash, profile identity/revision, embedding space, chunker target, and inspection failures.
- Container verification covers relational reads in the caller transaction, rollback of plan/items, competing destructive-plan exclusion, no execution before commit, execution after commit, and expired-claim recovery against saved migration targets. Existing activation/partial-plan retry and full MVP processing/query flow pass.
- No HTTP controllers/DTOs, SQL migrations, checkpoint/claim implementations, execution/recovery predicate, processing algorithms, or query strategies changed. General AI compatibility extraction and full feature relocation remain deferred.
