## 1. Immutable schema contracts

- [ ] 1.1 Add purpose-specific complete schema snapshot/resolution contracts under `schemas.contracts`, with defensive copies of nested collections; verify focused tests prohibit mutation and exclude persistence records, external clients, and secrets.
- [ ] 1.2 Implement exact stored-content/hash and expected ID/hash snapshot mapping; verify `ActiveSchemaResolverTest` equivalents cover missing resources, changed active ID, and changed content without an identity change.

## 2. Knowledge-base association ownership

- [ ] 2.1 Add schemas-owned KB admission/provisioning and association ports with immutable facts, plus knowledge-base public capabilities and mapping-only bootstrap adapters; verify focused port/adapter tests preserve existing operation errors and add no transactions or policy.
- [ ] 2.2 Move KB/schema association entity/composite ID/repository and locking/mutation operations to knowledge-base ownership, retaining table/constraint mappings; verify escalated association persistence tests preserve attach idempotence, single-active state, and deactivate/select ordering.
- [ ] 2.3 Remove KB/association access from schema definition persistence and compose global versus KB-scoped status/listing through association facts; verify registry tests cover shared schemas, global active status, scoped active status, and inactive listings.
- [ ] 2.4 Verify admission/association reads and mutation join registry transactions and activation still takes the KB lock; use escalated rollback and competing-activation tests to confirm no new commit boundary or weakening of the existing lock/constraint behavior.

## 3. Registry and schema consumers

- [ ] 3.1 Move registry definition records/ports/adapters, parser/validator rules, and workflows under schema ownership, preserving query/entity names and identity/hash mappings; verify registry/parser/validation tests and escalated `SchemaRegistryIntegrationTest`.
- [ ] 3.2 Replace registry KB accesses and optional null fallbacks with required ports, retaining operation-specific managed-KB/provisioning behavior; verify create/attach/activate and active update/delete guards with explicit test capabilities.
- [ ] 3.3 Preserve after-commit activation reprocessing and same-target activation idempotence; verify no plan scheduling before commit or on rollback/repeated activation, plus predecessor activation/reprocessing tests.
- [ ] 3.4 Move active resolution behind schema public snapshots and migrate document extraction/expected-target checks; verify extraction and immutable reprocessing targets preserve schema identity/hash guards.
- [ ] 3.5 Adapt controller mapping and named legacy schema entry points to delegate to the same registry/resolution capabilities; verify unchanged HTTP contracts and that existing search/draft/reprocessing callers compile without duplicated lookup policy.

## 4. Discovery input and workflow boundaries

- [ ] 4.1 Add schemas discovery document-input/file-parsing ports and documents public source acquisition/parsing capabilities with defensive copies and no path/record exports; verify owned/missing/foreign document behavior and that input preparation has no processing/graph/storage-write effects.
- [ ] 4.2 Add mapping-only discovery integration adapters and move `DiscoverySourcePreparer` onto the input ports and KB admission capability; verify exact source-count/guidance validation, byte bounds before parsing, document/text/file ordering, and established read/parser failures.
- [ ] 4.3 Preserve character/chunk limits, fingerprints and stable source/chunk IDs in schema-owned preparation; verify mixed-source preparation tests and no model calls before every accepted input passes validation.
- [ ] 4.4 Move discovery orchestration, prompt/model-result interpretation, deterministic aggregation, and failure/deadline collaborators under `schemas.discovery`; verify existing discovery model-output, analyzer, aggregator, classifier, and service tests preserve behavior.
- [ ] 4.5 Update exact existing durable draft imports of reused discovery collaborators without changing source revisions, storage, or evaluation policy; verify deterministic draft analysis/evaluation tests and review-only/no-request-persistence assertions.

## 5. Enforcement and integrated validation

- [ ] 5.1 Extend architecture rules for schema snapshots, registry/discovery internals, association ownership, document extraction, and mapping-only integration; verify forbidden repository, persistence-value, transaction, and feature-to-bootstrap dependencies fail checks.
- [ ] 5.2 Remove step-4 synchronous discovery/document-schema exceptions and freeze remaining exact draft/evaluation/search/reprocessing/support bridge dependencies with retirement steps; verify exact-set assertions and all predecessor isolation rules.
- [ ] 5.3 Run `./mvnw test -Pfast`; verify deterministic registry/discovery and inherited document/AI/reprocessing coverage passes.
- [ ] 5.4 Under escalated execution, run `SchemaRegistryIntegrationTest`, `SchemaDiscoveryIntegrationTest`, association transaction/concurrency coverage, and focused document extraction/activation-reprocessing tests; verify snapshots, status, locking, after-commit behavior, and ownership.
- [ ] 5.5 Under escalated execution, run `./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest`; verify schema registration/activation, document extraction, and queries retain their public behavior.

## 6. Documentation and readiness

- [ ] 6.1 Update architecture/schema/discovery portal pages, roadmap status/link, and overlapping README/AGENTS/CLAUDE facts; verify step 5 is recorded accurately while draft/evaluation/search/support migrations remain pending.
- [ ] 6.2 Align `src/site/site.xml` for any added portal page and run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` plus `./mvnw site`; verify alignment and portal generation succeed.
- [ ] 6.3 Validate the change and document remaining exception retirement in steps 6–9; verify `openspec validate establish-schema-registry-discovery-boundaries --strict` and coherent follow-on ownership contracts.
