## 1. Residual inventory and compatibility anchors

- [ ] 1.1 Inventory every architecture exception assigned to step nine and each affected legacy support/assembly class; record origin/target, final owner, replacement capability or permitted support role, and enforcing rule in the design; verify all document/schema/draft/search inventories are accounted for, including the 74 outward and 14 inward search baseline pairs.
- [ ] 1.2 Audit affected SQL mappings, JSON/snapshot fields, enum/string values, fingerprints, reflective/class-name references, bean names, qualifiers, schedules, property binding and persistence scans; verify an explicit compatibility checklist and historical fixtures cover each relocation-sensitive identity.
- [ ] 1.3 Inspect touched transactional self-invocation allowances and record existing transaction participation/propagation separately from ownership exceptions; verify no proposed change adds a transaction boundary or broad package exemption merely to satisfy architecture checks.

## 2. Typed settings ownership

- [ ] 2.1 Establish immutable settings-owned typed access contracts and values; migrate consumer references to those surfaces; verify existing field/default/alias precedence fixtures and architecture checks reject consumer dependencies on settings persistence or management implementations.
- [ ] 2.2 Consolidate settings controller/API models, catalog, codecs, validation, lifecycle, override ports and relational adapters; verify settings API contracts, sensitive masking, optimistic conflicts, atomic bulk validation, legacy hybrid retirement and restart/live lifecycle behavior with existing settings tests.
- [ ] 2.3 Move query-policy composition from settings to search using the same typed query snapshot; verify query validation/generation/execution/ask retain effective rows, timeout, keyword policy and response metadata, and settings has no search-policy dependency.
- [ ] 2.4 Add a settings-owned chunk-revision inspection port and documents public supplied-snapshot calculation capability with a bootstrap mapping; verify exact existing revision/hash outputs across strategies and aliases, no live-settings re-read in the calculator, no startup dependency cycle, and no automatic reprocessing.
- [ ] 2.5 Move owned startup configuration values to feature/support surfaces and migrate aggregate-property dependencies incrementally; verify existing property names/profile defaults bind identically and feature consumers no longer import application configuration factories.

## 3. AI and tokenizer ownership

- [ ] 3.1 Add or extend AI public non-secret profile facts/default/existence capabilities; migrate foreign workflow consumers away from mutable profile records and profile-management implementations; verify missing-resource behavior, immutable contracts and rejection of credential/provider-client leakage.
- [ ] 3.2 Consolidate AI profile controller/models, state, management, repository ports and relational adapters under AI; verify API key omission/retention, default seeding/uniqueness, assignment-safe update/delete admission, revisions and optimistic conflicts with existing profile tests.
- [ ] 3.3 Move tokenizer identifiers and embedding-model identity/resolution policy to AI, consolidating existing tokenizer compatibility rules; migrate profile/settings/document consumers; verify supported explicit/model-resolved/conservative/legacy identifiers, historical count modes, revisions and compatibility outcomes are unchanged without an AI-to-document implementation dependency.
- [ ] 3.4 Establish AI-owned model-resolution/context capabilities and relocate runtime factory, embedding/provider adapters and resolver mechanics; verify cache keys/invalidation, profile fallback/client selection, timeout/retry options, nested context restoration and failure cleanup with mocked model tests.
- [ ] 3.5 Migrate document, schema and search model boundaries to the AI capabilities while retaining feature prompts and response interpretation; verify captured model/profile semantics in dry extraction, draft analysis and advanced search, and architecture checks confine provider SDK handles to model adapters/AI provider implementations.

## 4. Knowledge-base and residual schema ownership

- [ ] 4.1 Consolidate remaining knowledge-base API, lifecycle/management state, repository ports and relational adapters; use public AI capabilities for defaults/existence/compatibility facts; verify provisioning, scoped reads, schema/profile associations, locks, incompatible assignment rejection and cleanup-before-relational-delete behavior.
- [ ] 4.2 Replace remaining foreign knowledge-base service/lifecycle dependencies with existing or extended public admission/profile capabilities and consumer mappings; verify missing-resource precedence, scope, non-secret facts and synchronous caller transaction participation.
- [ ] 4.3 Move legacy schema controller and schema/discovery API models plus chunk migration API mapping to the appropriate schema areas; verify unchanged routes, request/response fields, discovery statuses, error mapping and registry/reprocessing admission behavior.
- [ ] 4.4 Place remaining schema generation/example prompts, normalization, interpretation and model adapters with schemas, consuming AI capabilities; verify existing generation/example/mock-provider behavior and deterministic helper boundaries without leaving schema business logic in generic support.

## 5. Shared indexes and common support

- [ ] 5.1 Establish public shared vector-index contracts and graph adapters, placing embedding-space identity policy under AI; migrate document/search callers; verify exact names/labels/partition hashes, dimensions, scoped membership, legacy classification and idempotent creation against existing index fixtures.
- [ ] 5.2 Consolidate lexical-index identity, membership, readiness and retirement support behind governed contracts/adapters; migrate callers; verify scoped analyzer/index configuration, child membership, legacy readiness, online/deadline behavior and cleanup without document-to-search implementation access.
- [ ] 5.3 Move search-specific metric interpretation into a search-owned adapter while keeping generic model/workflow observations independent of search; verify unchanged metric names/tags/counts, hierarchy, abstention/repair/terminal reporting and observation privacy fixtures.
- [ ] 5.4 Define governed public surfaces for metadata-only logging, common immutable pagination/error bases, binary storage and transaction annotations; move feature-specific API values to their owners and global problem adaptation to HTTP assembly; verify unchanged pagination/problem fields, binary namespaces and journals, sensitive-data exclusions and store-qualified routing.

## 6. Application assembly

- [ ] 6.1 Relocate configuration factories, provider/support registration and schema startup resource loading to bootstrap; verify preserved bean names/conditions/qualifiers, executor/scheduler settings, default seeding and schema startup behavior with focused configuration/startup coverage.
- [ ] 6.2 Update persistence entity/repository scans and explicit relational/graph wiring for all relocated owners; verify scan completeness, primary `transactionManager`, `neo4jTransactionManager`, transaction-aware `neo4jTemplate`, database selection and repository routing in application-context/persistence tests.
- [ ] 6.3 Update integration mappings to final public capabilities and immutable values; verify all mappings remain free of repositories, SDK/filesystem clients, transactions and business policy, and synchronous reads retain caller transaction participation.

## 7. Permanent architecture rules

- [ ] 7.1 Add final owner/feature-graph/public-contract rules for documents, schemas, search, knowledge bases, AI and settings; verify negative fixtures reject foreign mutable state, workflow/persistence bypasses and unsupported source dependency directions while earlier boundary fixtures continue to pass.
- [ ] 7.2 Add support/domain/model rules for immutable typed settings, tokenizer ownership, feature-independent observations, index adapters, common support surfaces and provider client confinement; verify both allowed consumers and forbidden neighboring implementations with negative fixtures.
- [ ] 7.3 Add distinct assembly and mapping rules, including prohibition of feature/support dependencies on bootstrap factories; verify wiring can reference concrete owned implementations while integration mappings cannot use those privileges.
- [ ] 7.4 Retire every roadmap step-nine frozen pair/resource/assertion after its replacement rule applies; remove stale relocated legacy graph-client exceptions and preserve only individually assessed independent transaction constraints; verify zero remaining step-nine exceptions and complete inventory-to-rule accounting without broad legacy allowances.

## 8. Integrated behavior verification

- [ ] 8.1 Run `./mvnw test -Pfast`; verify deterministic settings/profile/tokenizer/chunking/schema-generation/query/search/model/observation/codec/lifecycle and architecture checks pass, resolving ownership-migration regressions while preserving established behavior.
- [ ] 8.2 Run focused settings/AI-profile/knowledge-base relational tests plus affected document/schema draft/evaluation/publication/reprocessing persistence and startup tests using escalated execution where Testcontainers runs; verify historical snapshots, optimistic races, assignment safety, independent checkpoints, caller participation and recovery after external success/checkpoint failure.
- [ ] 8.3 Run focused query validation/execution and advanced-search text/graph/ranking/run integration coverage with escalated execution; verify index identities/readiness, scope/provenance, captured profiles/settings/schema, claims/cancellation and recovery remain compatible.
- [ ] 8.4 Run `EndToEndMvpFlowIntegrationTest` and the complete credential-free suite with escalated execution; verify canonical startup/application wiring and both store boundaries without changing or deleting operational data volumes.

## 9. Documentation and completion

- [ ] 9.1 Update the architecture portal and affected settings/AI/knowledge-base/search/schema/support pages, `docs/MODULARIZATION_DESIGN.md`, README, AGENTS and CLAUDE to describe final ownership and implemented step-nine facts; verify overlapping statements agree and every new portal page is in `src/site/site.xml`.
- [ ] 9.2 Run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` and `./mvnw site`; verify documentation alignment and portal generation succeed with valid navigation/links.
- [ ] 9.3 Run `openspec validate finalize-support-boundaries-assembly --strict` and review the final implementation diff against the inventory/spec scenarios; verify all tasks have evidence, historical contracts need no SQL/binary migration, every step-nine exception has retired, and the roadmap is complete.
