## Context

See `proposal.md` for motivation and scope. The roadmap assigns query/ask and advanced search to one search feature. Their controllers, DTOs, policy values, services, repository ports, and relational/graph integrations currently span legacy packages.

`AdvancedSearchRunService` reads knowledge-base and schema repositories. It evaluates readiness before reserving executor capacity, then evaluates readiness again inside the run-creation transaction and compares profile ID/revision. It captures settings, evidence options, profile ID/revision, and exact stored schema ID/hash/content before saving the queued run. Worker processing consumes persisted schema content rather than replacing it with current registry content. Startup recovery marks stale work interrupted rather than replaying it.

`AdvancedSearchReadinessService` checks schema existence without parsing. It resolves the active/default profile, constructs clients without an external provider request, and checks embedding configuration/compatibility only when embedded chunks exist. Empty corpus and absent active schema remain informational. `AdvancedSearchCitationMetadataService` performs one scoped batch lookup capped at 128 IDs; `DocumentMetadataTextRetriever` performs bounded relational filename/content-type matching before retrieving owned chunks.

`SchemaSnapshots`, `StoredSchemaSnapshots`, and registry capabilities already provide immutable schema inputs. `DocumentSourceInputs` provides individual inspection and parsing, but lacks batch citation metadata and metadata-filter selection. AI owns compatibility through non-secret target values, while profile/model/client support still occupies legacy packages scheduled for step 9. `EmbeddingSpaceIndexService` serves document writes as well as retrieval-related support; lexical index support similarly spans processing/cleanup and search. These shared maintenance seams must not be moved wholesale into search.

The exact step-8 inventories are in `ArchitectureBoundaryTest`: schema bridge edges, four document metadata edges, and the final compatibility-policy callers. Existing query, planning, retrieval, ranking, answering, result-codec, lifecycle, OpenAPI, and integration tests provide behavior anchors.

## Goals / Non-Goals

**Goals:** Establish owner-specific search implementations and effect boundaries, retain caller transaction participation and bounded reads, and make architecture verification reject foreign internal access after step 8.

**Non-Goals:** Change admission failure precedence, schema-race behavior, provider revision enforcement, query syntax policy, retrieval/ranking/answer algorithms, cancellation/recovery, or serialized snapshots. Complete general AI/settings/observability/support relocation, shared index-maintenance reorganization, and schema API/bootstrap cleanup in step 9. Do not introduce SQL migrations, build modules, remote execution, or cross-store transactions.

## Decisions

### 1. One search feature with responsibility-oriented areas

Group existing implementations under `search.query`, `search.retrieval`, `search.ranking`, `search.answering`, and `search.runs`. API mapping, application workflows, deterministic values/rules, ports, and adapters live with their respective owner where useful; do not create empty layers. Planning belongs with the search workflow consuming it. Preserve ordinary concrete collaboration inside search rather than introducing an interface for every helper.

Move query and advanced-search controllers/API values; planning, typed graph validation/rendering, text and graph retrieval, parent-context expansion, fusion/ranking, citation assembly, sufficiency/follow-up/answer synthesis; durable run/attempt/result state, codecs, checkpoints, recovery/retention, and owned persistence. Preserve explicit entity/table mappings, JSON fields/enums, payload versions, prompt/contract revisions, and serialized settings/schema/result shapes. Inspect serialization for class-name assumptions before relocation.

Alternative: move only services. Rejected because API mapping, deterministic policy, and persistence would remain distributed and bypassable. Alternative: separate query and advanced-search features. Rejected because the roadmap confirms one search owner and they share query/evidence mechanics.

### 2. Public facts preserve schema and knowledge-base semantics

Add search-owned ports for managed knowledge-base admission/existence and schema facts, implemented by mapping-only adapters under `bootstrap.integration.search`. Reuse provider public capabilities and immutable schema values. Extend provider capabilities only for facts not already available; do not expose knowledge-base/schema records or repositories.

Distinguish optional active-schema availability, stored schema capture, active parsed resolution, and parsing a stored run snapshot. Readiness must preserve its existence-only check; malformed stored content must not become a new readiness blocker. Run capture must preserve exact stored content/hash, including its current missing-definition failure behavior. Query generation/validation can consume `SchemaSnapshots` complete parsed values. Parsing an advanced-search run's captured content uses a registry public parsing contract and retains the captured identity/hash; it must not substitute a newer active schema. Replace every step-8 `ActiveSchemaContext`, resolver, parser, and schema repository dependency with public values/contracts.

Synchronous capability calls join the caller's relational transaction. Preserve creation order: initial readiness, capacity reservation, transactional knowledge-base admission, repeated readiness, profile ID/revision comparison, schema/settings capture, queued save, dispatch. Release reservations through existing failure/cancellation paths. Do not add locks, an encompassing processing transaction, or stronger snapshot consistency than current behavior.

Alternative: route readiness and creation through parsed active resolution. Rejected because it changes text-only admission and parsing/failure precedence. Alternative: resolve current schema during worker execution. Rejected because it changes durable run snapshot semantics.

### 3. Documents supplies bounded metadata; search owns retrieval

Expose a document-owned public metadata capability for scoped batch lookup by document IDs and bounded filename/content-type selection. Return immutable purpose-specific metadata or identifiers, preserving existing filtering, ordering, missing/foreign handling, and bounds. Do not expose binary paths, content, or document records. Map this capability to search consumer ports without moving search ranking, citation fallback, warning, or deadline policy into documents or the bridge.

Citation enrichment keeps one batch lookup across evidence and contexts, deduplication, the existing 128-ID limit, fallback labels, and `SOURCE_METADATA_UNAVAILABLE`. Metadata retrieval retains its candidate limit, request deadline checks, and branch-local diagnostics. Empty selection, foreign scope, and replacement/deletion must retain existing outcomes.

Graph-native chunk/evidence/parent-context retrieval belongs to search-owned ports and Neo4j adapters. It need not call a documents processing repository merely because documents originally wrote the artifacts. Preserve same-scope filtering before limits, child text citations, authoritative extraction-parent graph citations, and context expansion without fabricated provenance.

Alternative: repeat individual `DocumentSourceInputs.inspectOwned` calls. Rejected because it loses batch query bounds. Alternative: export a document repository interface as a contract. Rejected because it retains foreign persistence ownership.

### 4. Search owns query/retrieval effects; shared maintenance remains explicit

Move query execution and planner `EXPLAIN` mechanics, including the existing `QueryNeo4jExecutor`, behind search-owned ports/adapters. Move graph/text/parent-context retrieval repository implementations and advanced-search relational persistence with search. Workflows consume immutable adapter observations rather than Neo4j driver nodes/paths; adapter mapping retains public response shapes. Shared infrastructure supplies the configured driver/database, connections, and transaction routing. Preserve row bounds, parameter handling, read-only validation, deadlines, and graph evidence scoping.

Retain lexical legacy-corpus labeling, idempotent index readiness, and online waits as existing bounded retrieval support effects; search does not gain document-processing or extracted-fact writes. Keep the shared index-maintenance services/ports required by document persistence and cleanup as exact step-9 seams. Search-owned wrappers can consume that support, but documents must not acquire a source dependency on search. Index identities, labels, names, partition hashing, and legacy quarantine/backfill behavior remain unchanged.

Alternative: put all Neo4j operations in a shared graph feature. Rejected because it obscures operation ownership. Alternative: move shared index maintenance with retrieval. Rejected because it gives document writes/cleanup a dependency on search and expands this slice into step 9.

### 5. AI owns compatibility; search keeps business interpretation

Replace the two `EmbeddingSpacePolicy` callers with AI-owned compatibility and immutable embedding target values. Readiness retains its blocker ordering and exception classification; dense retrieval retains batch embedding, compatible KB index selection, channel diagnostics, and provider resolution. Delete the obsolete policy bridge when production callers are gone. Do not duplicate identity/tokenizer rules in search.

Provider client construction, credentials, and non-secret profile inspection belong to AI. Existing AI support may be consumed through search-owned model adapters or narrow ports. Where legacy AI support is required until step 9, record exact origin/target seams rather than granting workflows broad legacy access. Search values and integration facts contain no profile records carrying keys, provider clients, or secrets; client objects remain inside model adapters. Business prompts, ranking policy, and answer interpretation remain search-owned.

Alternative: copy the compatibility policy into search. Rejected because AI is already its established owner. Alternative: consolidate all AI support here. Rejected because it expands the agreed step-8 scope across every feature.

### 6. Retire exact exceptions with positive rules and behavior anchors

Remove step-8 entries from `FROZEN_SCHEMA_BRIDGE_EDGES` and `FROZEN_DOCUMENT_EDGES`, and retire `FROZEN_EMBEDDING_POLICY_CALLERS` with the removed bridge. Add rules for search ownership, foreign public-contract access, deterministic policy isolation, search effect adapters, immutable boundary values, mapping-only integration, and no feature-to-bootstrap dependency.

Inventory residual AI/settings/logging/observability/transaction/shared-index support before moving code, then retain only exact seams assigned to step 9. Update existing exact paths where necessary without broadening target sets. Preserve earlier document/schema/knowledge-base/AI rules and forbid documents/support from depending on search implementations. Controller-level shared error mapping and assembly scanning may reference search API/contracts; foreign business workflows may not reach search repositories or implementation classes.

Behavior anchors include readiness without provider requests, empty/text-only admission, repeated transactional readiness/profile races, stored schema/settings/result readability, branch-local failures, bounded batch metadata, query guardrails, ranking/answer determinism, citation provenance, claims, cooperative cancellation, late-result suppression, retention, and interrupted recovery without replay.

## Risks / Trade-offs

- [Relocation changes persistence scanning, proxies, or serialization] -> Preserve mappings and payload versions; verify startup, historical snapshots/results, transaction participation, claims, and cleanup.
- [A unified schema contract changes optional/malformed-schema behavior] -> Keep availability, stored capture, active parsing, and captured-content parsing distinct and assert current failure order.
- [Metadata wrappers introduce N+1 reads or scope leaks] -> Verify one bounded citation lookup and bounded metadata selection with duplicate/missing/foreign IDs.
- [Index support relocation introduces documents-to-search dependencies] -> Keep shared maintenance seams exact for step 9 and verify stable index identities and legacy retrieval behavior.
- [Provider adapters leak credentials or shift business policy] -> Keep secrets/client construction inside AI/model adapters and preserve metadata-only logs and existing blocker/branch classifications.
- [Refactor changes race/recovery behavior] -> Preserve current transaction/proxy/checkpoint boundaries and run targeted concurrency, cancellation, and restart integration coverage.

## Migration Plan

1. Inventory search ownership, exact dependencies, persisted forms, tests, and residual support seams. Add necessary immutable provider capabilities, consumer ports, and mapping adapters.
2. Consolidate query and advanced-search responsibilities, move owned relational/graph/model adapters, and preserve required component/entity/repository/executor wiring.
3. Retire step-8 exceptions and compatibility policy; enforce search boundaries while retaining exact step-9 seams.
4. Run deterministic behavior and architecture checks, targeted persistence/retrieval/cancellation/recovery integration coverage, and canonical end-to-end coverage. Docker/Testcontainers require escalated execution.
5. Synchronize portal, roadmap, README, AGENTS, and CLAUDE; run documentation alignment, site generation, and strict OpenSpec validation.

Deploy the same application against existing PostgreSQL, Neo4j, and filesystem artifacts. Rollback reverts code and wiring while retaining compatible run histories, result payloads, schemas, chunks, evidence, and indexes. No store reset or data rewrite is required.
