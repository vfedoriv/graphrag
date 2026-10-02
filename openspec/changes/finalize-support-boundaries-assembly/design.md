## Context

See [proposal.md](proposal.md) for motivation and scope. Steps 1–8 already supply consumer-owned ports, immutable provider capabilities, transaction-free bootstrap mappings, and owned adapters. Step 9 completes those boundaries within the existing application.

The inspected residual inventory is concrete:

| Current seam | Final disposition |
|---|---|
| `service.RuntimeSettingsService`, settings controller/DTOs, override records/repositories, settings infrastructure | Settings owns API, application, immutable typed contracts/domain, ports, relational adapters, catalog/codecs and live appliers. |
| `RuntimeSettingsService.queryPolicy()` returning search `QueryPolicy` | Settings returns its immutable typed query settings; search constructs its own policy. |
| `RuntimeSettingsService.effectiveChunkerRevision()` constructing document chunking strategies and hash rules | A settings-owned revision-inspection port delegates through bootstrap to a document public calculator using the supplied immutable settings snapshot. |
| `domain.AiProfileNode`, `service.AiProfileService`, `AiRuntimeModelFactory`, `AiProfileContext`, `infrastructure.ai.ProfileScopedAiClientResolver`, shared embedding clients and AI relational adapters | AI owns profile state/management, non-secret facts, execution context, model resolution and provider construction; consumers use public capabilities with SDK handles confined to model adapters. |
| AI/profile/KB references to `documents.domain.chunking.TokenizerId`; existing `ai.domain.EmbeddingTokenizer` | Consolidate identity/resolution/compatibility in AI, preserving existing identifiers and policy revisions; documents retains token counting/chunk construction mechanics. |
| `service.KnowledgeBaseService`, lifecycle, controller, record and persistence adapters | Knowledge bases owns lifecycle, associations, API and persistence; foreign admission/profile reads use public capabilities. |
| `service.EmbeddingSpaceIndexService`, embedding/lexical index identities, `repository.LexicalIndexRepository` and graph implementations | Shared infrastructure owns index mechanics behind narrow public support contracts; AI owns embedding-space identity rules. |
| `observability.AdvancedSearchMetrics` imports search outcomes, attempts and statuses | Search owns metric interpretation and its Micrometer adapter; generic observation support remains independent of search. |
| Logging, page/error values, binary storage, transaction annotations | Explicit public support surfaces; feature-specific API types stay with their owner and global HTTP mapping is application assembly. |
| `config.PersistenceConfiguration` references feature entities/repositories; other configuration/executor/startup classes | Bootstrap owns application wiring/scanning; owned configuration values or support contracts replace feature dependencies on assembly properties. |
| `controller.SchemaController`, discovery DTOs, `ChunkMigrationController`, `service.SchemaBootstrapService`, legacy schema-generation helpers/adapters | Schema API/model behavior moves to the appropriate schema area; feature prompts/normalization/model interpretation remain schema-owned, startup resource loading/wiring remains bootstrap-owned. |

Architecture inputs include `FROZEN_DOCUMENT_EDGES`, `FROZEN_SCHEMA_BRIDGE_EDGES`, `FROZEN_DRAFT_LATER_EDGES`, other schema/support freezes, and the 74 outward / 14 inward search pairs in `src/test/resources/architecture/search-step9-edges.txt` and `search-external-step9-edges.txt`. These counts describe the inspected search baseline, not the whole repository. Inventory every step-9 set and affected legacy class before relocation and assign an owner and disposition; do not infer completeness from symbol indexing alone.

## Goals / Non-Goals

**Goals:** Replace each roadmap exception with owned implementation or an explicit permanent capability/support/assembly rule. Preserve behavior and independently committed checkpoints. Keep configuration assembly from becoming a business service and support from depending on feature internals.

**Non-Goals:** New layers for every helper, broad public implementation packages, separate Maven modules/processes, algorithm/provider changes, database migration, or automatic cleanup of unrelated historical transaction behavior. No new transitional step after step 9.

## Decisions

### 1. Finish ownership before removing legacy exception rules

Use coherent existing feature areas, adding small API/application/domain/contracts/ports/adapters areas where responsibility requires them. AI and knowledge bases receive their remaining management/persistence implementations; settings receives its complete catalog/lifecycle stack. Move remaining schema API and schema-generation responsibilities to schemas, preserving the existing controller routes and model business behavior. Bootstrap receives startup and assembly responsibilities.

Alternatives considered: keeping legacy services and relabeling them as public support would leave foreign mutable state accessible; indiscriminately renaming all files would hide responsibility changes. Instead, map each affected class to an owner and distinguish capability contracts from implementation.

Foreign features consume AI/knowledge-base facts through existing or extended purpose-specific public capabilities and consumer-owned ports, preserving scope checks and missing-resource behavior. They do not consume profile repositories, mutable records, profile-management workflows, or secret-bearing configuration. Existing stored-observation and profile-assignment bridges remain mapping-only.

### 2. Typed settings contracts flow outward; feature interpretation stays with the feature

Separate immutable typed settings values/access from settings CRUD, persistence records, catalog implementation and HTTP responses. Consumers receive typed snapshots with existing fields/defaults/alias precedence. Search composes `QueryPolicy` locally; deterministic rules can depend on immutable settings values but cannot resolve live settings through an effectful service.

For effective chunker revision reporting, retain the current settings hash inputs, order/canonicalization, strategy revisions, tokenizer/parser/representation revisions, and response fields. A settings-owned port accepts a complete immutable chunking settings snapshot and returns revision facts. A bootstrap adapter maps that snapshot to a documents public revision capability; documents executes the existing calculation. The calculation must not re-read live settings or invoke the settings service. This avoids a settings-to-documents source dependency and a settings/document bean construction cycle. The settings-owned port contains no document types; the document capability contains no settings implementation types.

Validation of types/ranges/atomic related-setting updates remains settings-owned. Chunk implementation and effective chunk revision derivation remain document-owned. Keep revision reporting distinct from profile/parser-specific processing snapshots where the current behavior distinguishes them.

Alternative: move chunk algorithms or search policy into settings. Rejected because support would then own feature behavior. Also reject a callback that captures live settings and computes a revision from a different snapshot.

### 3. AI owns profile facts, provider construction, context and tokenizer identity

Keep API keys within AI persistence/provider construction. Expose immutable profile ID/revision, model/endpoint metadata, embedding target and resolved tokenizer facts only where required. Preserve default seeding, assignment compatibility, optimistic updates, cache keys/invalidation, model selection fallback, timeout/retry settings, and captured-profile execution semantics.

Provide AI-owned model-resolution/execution capabilities. Provider SDK handles are permitted only at model adapter boundaries and AI provider implementations; immutable public fact contracts and business workflows cannot expose them. Context may carry profile identity and capture semantics through a public AI execution API while the SDK-bearing capture mechanism stays behind that API. Feature model adapters retain business prompts and interpretation. Consumer ports/bootstrap bridges translate feature capture inputs to AI capabilities when necessary.

Tokenizer IDs and deterministic provider/model tokenizer resolution belong to AI, consolidating with existing `EmbeddingTokenizer`. Documents retains count estimators, splitting strategies and historical restore behavior; it consumes AI tokenizer facts. Preserve `cl100k_base`, conservative `utf8-byte-v1`, legacy identifiers/count modes, compatibility comparisons and revision strings. Moving a Java enum must not alter string-based persistence or serialized snapshots.

Alternative: expose the moved mutable profile record as a public contract. Rejected because it would preserve secret/state coupling. An interface for every model helper is also unnecessary; use capabilities at actual consumer/provider boundaries.

### 4. Shared index support is an infrastructure capability

Place vector/full-text index identity mechanics, online readiness, membership assignment and scoped retirement behind narrow shared support contracts and graph adapters. AI continues to own deterministic embedding-space identity and compatibility. Documents owns chunk/fact writes and cleanup authorization; search owns retrieval policy, query effects, deadlines and branch outcomes.

Retain exact index names, label hashes, dimensions, configured database, analyzer configuration, legacy membership/backfill/quarantine behavior, online waits, deadline results and idempotency. Shared support must not interpret document processing state, search run state, or feature admission policy. No migration should rebuild indexes solely because package names changed.

Alternatives: search-owned shared maintenance would couple document writes to search; document-owned retrieval readiness would couple search to document implementation. Shared mechanics with narrow contracts serves both without either dependency.

### 5. Generic observability and common support have explicit public surfaces

Keep generic workflow/model observations, privacy controls, token metadata and metadata-only log helpers in observability/logging support. Move `AdvancedSearchMetrics` into a search-owned metrics adapter because its inputs encode search interpretation. Preserve metric names/tags/counts, observation hierarchy and capture limits. Generic support can use settings and AI public non-secret metadata, but cannot import feature outcome/attempt records.

Retain small common immutable pagination and error base values where genuinely shared. Move feature-specific DTOs/exceptions to the owning API. Global RFC 7807 adaptation belongs to bootstrap HTTP assembly and may reference feature API exceptions, not feature repositories or workflows. Shared binary storage stays a support capability; document/draft adapters retain owned namespaces/journals.

Store-qualified transaction annotations remain infrastructure support. Relational and graph adapters retain their transaction semantics, and synchronous capability reads retain caller participation. Pure values/rules do not acquire transaction or persistence dependencies.

Alternative: introduce a broad shared-domain package. Rejected because it would absorb feature-owned values without a precise dependency boundary.

### 6. Assembly privileges are separated from mapping privileges

Bootstrap configuration may reference concrete feature implementations, entities and repositories to assemble the application and scan persistence. `bootstrap.integration` adapters remain restricted to consumer ports, provider public capabilities, immutable mappings and wiring support: no repositories, provider SDKs, filesystem clients, transactions or feature policy. Business features and generic support must not depend on bootstrap implementations.

Relocate configuration factories/startup runners and preserve bean names, primary/qualifier selection, conditions, executor names, schedules and startup ordering. Bound configuration values belong to feature/support-owned types outside bootstrap; their registration/wiring stays in bootstrap. Split aggregate `AppProperties` dependencies into the necessary owned configuration surfaces while preserving property names, default values and binding behavior. Do not merely move the aggregate class into bootstrap and leave foreign callers importing it.

Persistence wiring preserves `transactionManager`, `neo4jTransactionManager`, `neo4jTemplate`, entity/repository scan coverage and configured database selection. Schema bootstrap can invoke the registry capability/implementation as composition startup, but registry policy remains registry-owned.

Alternative: blanket bootstrap permission for all adapters. Rejected because mapping adapters would gain persistence/policy privileges intended only for assembly.

### 7. Completion is measured by permanent rules and exception accounting

Record each exact step-9 origin/target, destination owner/public surface, and applicable final architecture rule in the implementation inventory. Remove retired inventories/assertions only after replacement rules cover their responsibilities. Add negative fixtures for support-to-feature dependencies, foreign profile/KB state, effectful domain rules, provider leakage, mapping bypasses and feature-to-bootstrap edges. Test both allowed support/assembly dependencies and rejected neighbors; package renaming alone is insufficient.

Enforce the roadmap public feature graph, consumer-port bridges for reverse runtime interactions, owner-only implementation access, pure deterministic rules, and permitted support contracts. Govern direct graph client access by adapter/assembly roles after moving the legacy embedding-index exception. No roadmap step-9 allowlist remains at completion.

Historical transactional self-invocation exceptions are a separate baseline. Inspect each touched exception, preserve established transaction participation, and remove stale exceptions as paths move. If a remaining self-call has deliberate existing semantics, retain a narrowly documented exact allowance under the permanent transaction rule; do not broadly exempt an owner package or silently add a transaction boundary to make the rule pass. This does not authorize retaining foreign ownership exceptions.

## Risks / Trade-offs

- [Profile or tokenizer relocation changes serialized identity] -> Audit SQL enum/string mappings, JSON fields, reflection/class-name references and historical fixtures before moving; preserve persisted identifiers and revisions.
- [Settings revision callback creates cycles or reads different values] -> Pass one immutable supplied snapshot to a stateless calculator and verify no settings accessor dependency in that calculator.
- [Context/model selection changes captured execution] -> Preserve nested context restoration, failure cleanup, default fallback and captured-model behavior with mocked provider coverage.
- [Wiring changes lose repositories/qualifiers] -> Explicitly verify scan coverage and fresh application startup with both persistence stacks and provider profiles.
- [Architecture rules allow renamed violations] -> Account for every baseline pair and use negative fixtures testing adjacent forbidden implementations and clients.
- [Index/metrics identity changes affect operations] -> Retain names/labels/tag values and verify against existing fixtures and graph integration coverage.
- [Large support migration becomes a behavior redesign] -> Sequence focused owner slices with existing behavior anchors; use separate proposals for discovered new behavior requirements.

## Migration Plan

1. Inventory all residual step-9 pairs, legacy owner classes and serialization/wiring assumptions. Map each to a final surface/rule and preserve explicit unrelated transaction baselines.
2. Establish immutable settings and AI/knowledge-base contracts, then move settings interpretation and tokenizer identity to their owners. Migrate consumers with behavior fixtures retained.
3. Consolidate remaining management/persistence/model and schema API responsibilities. Establish shared index support, relocate search metrics, and govern common support values.
4. Move application assembly/configuration/startup, update explicit scans and mappings, then replace each transitional ownership freeze with permanent positive/negative rules.
5. Run focused deterministic, persistence/graph/startup and canonical-flow checks; align portal/contributor/roadmap docs and validate the change.

Deploy as one normal application release with existing database and graph artifacts. No SQL or binary migration is planned. Preserve existing configuration/bean/serialization identities so rollback is a normal prior-application release; audit unexpected reflective/class-name serialization before accepting that claim. Keep operational state and data volumes intact. The proposal remains incomplete until all step-9 inventory entries have final dispositions and enforceable rules.
