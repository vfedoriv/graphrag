## Context

See [proposal.md](proposal.md) for motivation and scope. The first two roadmap changes establish consumer-owned schemas ports, immutable documents contracts, and synchronous mapping adapters under `bootstrap.integration.reprocessing`. Reuse that ownership pattern.

Current dependencies relevant to this slice:

- `KnowledgeBaseService.delete` checks existence, counts owned document records, rejects non-empty deletion, cleans graph artifacts, then deletes relational knowledge-base state. Its injected `DocumentChunkRepository` is otherwise only used by compatibility constructors.
- `EmbeddingSpacePolicy` retrieves embedded chunks, compares embedding-space identity and resolved tokenizer, and aggregates incompatible knowledge-base IDs. A missing/blank stored tokenizer resolves from the stored embedding model. A missing embedding-space identity remains incompatible; compatibility does not backfill legacy data.
- `AiProfileService` checks assigned knowledge bases before mutating a profile and checks assignment presence before deletion. `RelationalAiProfileRepository` currently implements both lookups by calling `KnowledgeBaseRepository`.
- Embedding-space identity hashes normalized endpoint, model, and dimensions. Tokenizer is a separate compatibility condition, not a new ingredient in the historical identity hash.
- Compatibility is used beyond profile management by processing, migration preparation, and search. These callers must keep equivalent behavior without forcing their full feature relocation.

The current architecture portal still defers general AI compatibility extraction. The roadmap status and step-2 link also lag its archived implementation. Documentation corrections belong to implementation, not this planning-only capture.

## Goals / Non-Goals

**Goals:** Make foreign state access purpose-specific and enforceable; separate stored embedding inspection from pure compatibility decisions; preserve synchronous reads, deletion ordering, immutable historical identities, and failure-before-mutation behavior.

**Non-Goals:** Introduce stronger cross-store consistency, optimize chunk/assignment queries, change cleanup effects, relocate every AI or knowledge-base class, replace model factories, or claim the final roadmap dependency graph is already enforced.

## Decisions

### 1. Knowledge bases request document facts and cleanup through their own ports

Introduce purpose-specific ports under `knowledgebase.ports`, with proposed names `OwnedDocumentState` (owned-record count) and `KnowledgeBaseArtifactCleanup` (cleanup scoped to one knowledge base). Provider contracts live under `documents.contracts`; document facades implement count reads through `DocumentUploadRepository` and cleanup through the existing `GraphArtifactCleanupService`. The public cleanup result, if needed, is an immutable count record, not that service's nested implementation type.

Mapping adapters under `bootstrap.integration.knowledgebase` implement consumer ports through provider capabilities. Knowledge bases retain existence checks, the non-empty conflict including document count, sequencing, and relational deletion. Documents retains graph/evidence/chunk cleanup and its existing lexical-index removal. Adapters perform no lifecycle decisions or store access.

Remove production document repository/cleanup service dependencies from `KnowledgeBaseService`, including repository-taking compatibility constructors. Update deterministic test construction to supply explicit ports rather than retaining null fallbacks that bypass guards. Any retained convenience construction must still require the affected safety capabilities.

Alternative: inject the cleanup service or repositories behind generic repository wrappers. Rejected because it retains implementation coupling and makes ownership unenforceable. Alternative: move knowledge-base deletion into documents. Rejected because lifecycle policy and associations belong to knowledge bases.

### 2. AI owns compatibility; documents supplies stored embedding observations

Introduce an AI-owned `StoredEmbeddingInformation` port under `ai.ports`, implemented by `bootstrap.integration.ai` through a document public capability. Its immutable observations contain the existing stored embedding-space ID, embedding model, and tokenizer ID needed for comparison. Include additional fields only if required by an existing caller; do not expose chunks, vectors, graph clients, or provider secrets.

Document inspection retains `findEmbeddedChunksByKnowledgeBaseId` semantics, including its current empty/null handling and knowledge-base scope. It returns stored facts without deciding whether a target is compatible. The integration adapter maps provider observations to AI-owned observations.

Place the deterministic comparison under `ai.domain`, taking a non-secret target descriptor and immutable observations. Preserve endpoint/model/dimension normalization and historical space IDs, exact identity comparison, and missing/blank tokenizer fallback using the stored model. Do not reinterpret missing space IDs as compatible. Keep exceptions and incompatible knowledge-base aggregation at the AI application boundary.

An AI-owned compatibility application capability reads the port and invokes the pure rule. Existing `EmbeddingSpacePolicy` may remain a thin compatibility bridge for unmigrated processing/search/reprocessing callers, delegating to that capability; it must no longer retrieve chunk records or embed a second rule implementation. AI must not depend on this legacy bridge. Cross-feature compatibility requests carry non-secret immutable target values rather than profile entities.

Preserve `spaceFor`, `hasEmbeddedChunks`, single-KB rejection, and multi-KB rejection behavior where existing callers need them. Retain identity utility compatibility entry points if necessary, but new rules and contracts must not depend on `AiProfileNode` or document persistence types.

Alternative: documents returns a compatibility boolean. Rejected because provider/model/tokenizer compatibility policy belongs to AI. Alternative: pass `DocumentChunkNode` through a port. Rejected because it exports document persistence and bypasses the desired boundary.

### 3. Profile assignments are knowledge-base facts exposed to AI

Add an AI-owned `ProfileAssignments` port with assignment presence and assigned knowledge-base IDs. A knowledge-base public capability implements these reads through the existing `KnowledgeBaseRepository` operations. A `bootstrap.integration.ai` adapter maps the capabilities; neither feature depends on the adapter.

Inject this port into `AiProfileService` for update/delete admission and remove `existsKnowledgeBaseAssignment` and `findAssignedKnowledgeBaseIds` from `AiProfileRepository` and its relational adapter. Keep the adapter responsible only for profile persistence. Profile update still evaluates all assigned knowledge bases before applying fields, changing revision/default selection, saving, or invalidating model clients; deletion still rejects default/assigned profiles.

Alternative: retain knowledge-base queries in the AI repository as an adapter detail. Rejected because that leaves a reverse ownership dependency hidden under persistence and blocks the target feature graph. Alternative: call `KnowledgeBaseService` directly. Rejected because its AI dependencies create a source cycle; the read capability remains independent of AI workflow implementations.

### 4. Preserve current transactions and failure ordering

Adapters are synchronous, in-process mappings without transaction annotations. Relational count/assignment reads participate in their callers' existing transactions; graph reads and cleanup preserve current store routing. Add no `REQUIRES_NEW`, after-commit cleanup, distributed transaction claim, or new snapshot cache.

An incompatible assignment leaves the active association unchanged. An incompatible shared-profile update leaves fields, revision, defaults, and assignments unchanged. Non-empty deletion performs no cleanup or relational deletion. Cleanup failure prevents the subsequent relational delete, while prior external effects retain existing partial-failure semantics. Preserve existing optimistic assignment/save checks and database constraints; do not claim to close existing cross-store races by introducing ports.

### 5. Enforce the migrated slice without blanket exemptions

Extend `ArchitectureBoundaryTest` to reject document internals from migrated knowledge-base lifecycle and AI compatibility/profile paths, and knowledge-base repositories/entities from AI profile persistence. Contracts are immutable purpose-specific values without repositories, persistence entities, provider clients, or keys. Pure compatibility has no repositories, adapters, application assembly, or feature implementation dependencies.

Integration adapters may depend only on consumer ports, provider public capabilities, boundary values, and required wiring support. Negative fixtures must detect both direct access and provider-contract bypasses, following the completed reprocessing tests. Production features never depend on bootstrap. Preserve changes 1 and 2's architecture rules.

Use exact class/path restrictions for legacy services still outside feature packages. If existing unmigrated callers require a transitional bridge, name its exact callers/dependencies and retirement slice (documents step 4, registry/discovery step 5, search step 8, or assembly step 9 as appropriate). No exception may permit the document or knowledge-base state accesses being removed here. Do not broaden allowances merely because other legacy services remain.

Alternative: enforce the final graph over every legacy service now. Rejected because it expands into later roadmap slices. Alternative: allow all shared `service` classes. Rejected because it silently permits new violations.

## Risks / Trade-offs

- Legacy tokenizer or space handling changes -> pure-rule tests with absent/blank tokenizer, known/unknown model, missing space ID, and incompatible target cases; unchanged identity fixtures.
- A constructor bypasses safety checks -> migrate existing unit-test construction and assert no default/null path skips count, cleanup, assignment, or compatibility capabilities in production.
- Reordered effects change rollback behavior -> focused workflow tests plus container-backed assignment/profile rollback and deletion cleanup/failure coverage.
- Mapping becomes business logic -> typed mapper tests and dependency guards; keep fallback/comparison in AI and document reads/cleanup in documents.
- Compatibility relocation breaks other callers -> retain only a delegating bridge and run processing, reprocessing preparation, and search compatibility coverage.
- Existing cross-store races remain -> retain documented semantics; concurrency redesign needs its own behavior change.

## Migration Plan

1. Add consumer/provider contracts and pure AI rules; wire document inspection/cleanup and knowledge-base assignment capabilities with mapping adapters.
2. Switch knowledge-base deletion/profile assignment and AI profile update/delete; remove foreign repository access and bypass constructors.
3. Delegate existing compatibility callers to the new AI capability and enforce scoped architecture rules.
4. Verify deterministic behavior, store-backed rollback/cleanup, and predecessor reprocessing regressions.
5. Align architecture/codebase portal pages, shared contributor facts, and roadmap completion/archive links. Run documentation alignment and site generation.

Deployment remains one application, with no SQL or serialized-format migration. Rollback reverts this slice while retaining the first two boundaries. Existing chunks, profiles, plan snapshots, and run history remain readable.
