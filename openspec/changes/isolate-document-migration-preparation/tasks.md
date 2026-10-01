## 1. Predecessor and preparation contracts

- [ ] 1.1 Confirm `isolate-reprocessing-execution-recovery` is implemented and its execution/recovery checks pass; reconcile final contract names with this design and verify no predecessor behavior or ownership decision is changed.
- [ ] 1.2 Add schema-owned preparation/read ports and document-owned selection/target/classification contracts using immutable purpose-specific values; verify contract tests and architecture checks exclude document entities, persisted schema plans, runtime chunker objects, and provider secrets.

## 2. Document preparation and mapping

- [ ] 2.1 Implement ownership-safe document summaries for all-owned and explicit-ID selection with existing ordering/deduplication behavior; verify foreign/missing IDs retain not-found behavior and selection tests preserve current ordering and hashes.
- [ ] 2.2 Move option/parser resolution, chunk presence inspection, completed-run revision matching, and document target construction into the document preparation facade; verify tests distinguish `NO_CHUNKS`, `CURRENT`, and `OUTDATED`, including stale source hashes and inactive/failed runs.
- [ ] 2.3 Expose existing chunker/embedding target identity and compatibility through preparation/inspection operations without changing typed settings access; verify tests preserve profile revision, tokenizer/chunker revisions, compatibility blockers, and target-change behavior.
- [ ] 2.4 Add integration mappings from document preparation values to schema-owned port values; verify mapping tests and representative snapshot comparisons preserve every canonical JSON field and fingerprint and restore the same runtime input through change 1.

## 3. Migrate plan preparation paths

- [ ] 3.1 Replace document access in activation selection and chunk candidate preparation while keeping selection policy in schemas; verify activation choice combinations, chunk selection modes, explicit ownership, and no-chunk/outdated inclusion tests pass.
- [ ] 3.2 Route preview and creation through the shared read-only preparation implementation, preserving all-owned classification scope, blocker priority, and pagination; verify preview creates no work and creation recomputes facts and rejects stale revisions/blockers without persisting plans/items.
- [ ] 3.3 Migrate retry and history/currentness preparation to the ports; verify successful matching items are preserved, unresolved targets are resnapshotted with lineage, and changed schema/profile/embedding/chunker targets retain current blocked/currentness semantics.
- [ ] 3.4 Remove remaining document repositories/entities, option resolvers, and chunking implementation dependencies from reprocessing orchestration; verify its only document interactions use consumer ports and schema plan persistence/claims remain unchanged.

## 4. Completed boundary and persistence verification

- [ ] 4.1 Remove change 1's preparation exceptions and extend architecture checks across all reprocessing preparation/currentness paths and integration adapters; verify forbidden document-internal dependencies fail and unrelated draft/discovery code gains no blanket exception.
- [ ] 4.2 Run `./mvnw test -Pfast`; verify preparation, snapshots, preview/create, retry, currentness, architecture, and predecessor execution/recovery tests pass.
- [ ] 4.3 Run relevant `SchemaDraftLifecycleIntegrationTest` creation/retry/recovery cases and `EndToEndMvpFlowIntegrationTest` with escalated execution; verify transaction participation, no partial plan persistence on failure, competing destructive-plan exclusion, scheduling after commit, and historical snapshot execution.

## 5. Documentation and review

- [ ] 5.1 Update architecture/codebase-tour and chunking-reprocessing portal pages plus overlapping facts in `README.md`, `AGENTS.md`, and `CLAUDE.md`; verify docs state completed reprocessing isolation while AI compatibility extraction and full feature relocation remain deferred.
- [ ] 5.2 Run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` and `./mvnw site`; verify both pass and any new portal page is registered in `src/site/site.xml`.
- [ ] 5.3 Review the diff and verify no HTTP/SQL/snapshot format, selection semantics, recovery predicate, query strategy, transaction guarantees, or processing algorithm changed; record the checks before completing the change.
