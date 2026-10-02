## Context

See [proposal.md](proposal.md), [the roadmap](../../../docs/MODULARIZATION_DESIGN.md), and [document consolidation](../consolidate-documents/design.md). Step 3 satisfies this slice's roadmap prerequisite; the chosen execution sequence completes step 4 first.

Current registry/discovery seams:

- `SchemaRegistryService` handles immutable identity, inactive updates, active-reference guards, KB provisioning/association, and after-commit reprocessing. Its optional constructors retain legacy null fallbacks.
- `RelationalSchemaDefinitionRepository` mixes definition persistence with association persistence and KB locking. `findAll` reports global active status; KB listing reports association-specific active status. Both semantics must survive separation.
- Activation takes the KB row lock, ensures an association, deactivates prior associations, and activates the selected one in the relational transaction.
- `ActiveSchemaResolver` reads KB and schema repositories and returns `ActiveSchemaContext` containing a mutable schema persistence record. `resolveExpected` already checks active ID and content hash.
- `DiscoverySourcePreparer` reads owned document records and binaries, parses documents/files, then applies discovery byte/character/chunk bounds, fingerprints, stable source IDs, and ordering. Durable draft analysis reuses discovery collaborators but has its own source revisions and storage.

## Goals / Non-Goals

**Goals:** Make complete schema values safe to consume; give registry, discovery, documents input, and KB associations enforceable owners; preserve synchronous transaction participation and observable behavior.

**Non-Goals:** Durable draft workflow migration; new schema revision counters; activation API redesign; stronger concurrency guarantees; provider-output changes; new persisted sources; dry extraction/evaluation redesign; general search relocation; final reprocessing organization.

## Decisions

### 1. Expose complete immutable schema values

Introduce purpose-specific public contracts under `schemas.contracts`: proposed `SchemaSnapshots` and `SchemaSnapshot` provide active resolution, expected ID/hash resolution, and stored-definition reads needed by existing consumers. Scope each method to observed usage rather than reproducing the repository API. Include KB scope where relevant, schema ID, name/version, source/format/status metadata and timestamps needed by existing consumers, exact stored content/hash, and immutable parsed values. Deep-copy nested lists/maps; no persistence entity or JPA optimistic version crosses the public boundary unless a concrete consumer already needs that field.

Use stored content hash as the content revision token. `name + version` remains identity and cannot detect inactive content replacement. Never regenerate JSON to calculate a new hash during relocation. Keep missing-KB, missing-active-association, missing-definition, and expected-target errors equivalent. Preserve the distinction between global active status and KB-specific active status.

Migrate document extraction and its expected-target checks to public snapshots. Search generation/validation can retain their legacy resolver entry point temporarily, but it becomes a thin delegation to the shared snapshot capability, using immutable values rather than schema/KB repositories. If a legacy consumer requires the old context shape, restrict conversion to its named bridge; no new caller may use it. Registry/draft/reprocessing compatibility entry points remain narrow, with final removals assigned to steps 6/7/8/9. A bridge must not duplicate lookup/validation policy.

Alternative: return a detached persistence record. Rejected because it remains mutable and exports internal state. Alternative: use schema version as revision. Rejected because inactive content can change without identity changes.

### 2. Separate definitions from associations without splitting activation commits

Move registry definition records, ports, adapters, parser/validator rules, and workflow implementation into `schemas.registry`; public schema value types live with contracts, not implementation packages. Definition persistence owns only definition rows and supplies raw immutable/internal definition values. Registry composes active status and KB listing from association facts; no schema adapter directly queries KB/association repositories.

Introduce schemas-owned ports under `schemas.registry.ports` for KB admission/provisioning and schema association operations. Knowledge-base public capabilities under `knowledgebase.contracts` implement existence/provisioning, active ID and associated IDs, global active-reference inspection, attach/activate/detach operations, and scoped status facts needed by current reads. Keep identities and immutable facts in the contracts. These are association operations, not a generic KB repository facade. Knowledge bases own `KnowledgeBaseSchemaEntity`, its composite ID, association repositories, and the KB row locking needed for activation.

Mapping adapters under `bootstrap.integration.schemas` synchronously implement schemas ports using KB capabilities. They add no transaction annotations or policy. The KB mutation capability joins the caller's existing relational transaction; preserve the activation lock, association creation idempotency, deactivate/select ordering, single-active constraint, and flush behavior. Definition creation plus optional attachment, guarded definition deletion plus detach, and activation retain their existing transaction scope. Registry validates schema existence/identity and active-reference constraints; KB owns association mutations and does not call schema implementation to revalidate them. SQL foreign keys retain referential integrity.

Preserve the managed-KB/provisioning behavior of each public operation, same-schema activation idempotence, global active-reference update/delete guards, and no automatic reprocessing for an already-active target. Keep after-commit plan scheduling schema-owned and only after an actual activation transition. Remove optional null fallbacks in production construction; tests must supply required ports rather than bypass admission. Preserve existing public HTTP behavior while migrating construction.

Alternative: wrap the existing mixed persistence adapter and declare it private. Rejected because it hides foreign state ownership. Alternative: separate commits or `REQUIRES_NEW` for associations. Rejected because it weakens the existing relational guarantees.

### 3. Separate discovery input acquisition from discovery policy

Introduce schemas-owned `DiscoveryDocumentInputs` and `DiscoveryFileParsing` ports under `schemas.discovery.ports`, mapped through bootstrap to purpose-specific documents capabilities. Proposed document capability `DocumentSourceInputs` reads an owned persisted document and provides parse support for ephemeral files. Document ownership checks use KB/document scope and preserve identical missing-versus-foreign not-found behavior. Inputs/results carry only necessary identity/name/content-type metadata, exact byte count, source fingerprint or exact bytes needed to preserve existing hashing, and parsed text. Use defensive copies for bytes if included. No content URI, local path, record, or parser instance crosses the boundary.

Preserve pre-parse byte checks: the read result must make source byte count available before parsing when current control flow applies bounds before parser invocation. A schemas workflow obtains content metadata/bytes, checks individual/total byte limits in existing source order, then requests parsing through its port; a read-and-parse convenience method cannot move the limit check after parsing. Request file bytes remain ephemeral and use the same parser routing behavior as today. Schema input adapters translate values only; document-owned adapters perform binary access and parsing.

Schemas retains source-count and guidance validation, text-source handling, trimming/blank checks, per-source/aggregate character bounds, analysis chunk limits, fingerprint algorithms, ordinal/source/chunk IDs, independent source support, deterministic aggregation, conflict policy, and review-only projection. Preserve document/text/file processing order and failure precedence. No input preparation creates processing/extraction runs, embeddings, graph artifacts, or registered schemas. Every accepted input is validated before any model call; files/text are not persisted.

Move synchronous discovery workflow and its prompt/result/aggregation/failure collaborators under `schemas.discovery`. Preserve active-profile selection, bounded attempts/deadlines, structured-output handling, response envelopes, and metadata-only logging. Durable draft analysis can keep explicitly frozen imports of the relocated reusable collaborators until step 6; it retains its own snapshot/revision/storage policy. Do not reuse synchronous input preparation to rewrite draft sources or held-out evaluation.

Alternative: return fully prepared discovery chunks from documents. Rejected because limits, chunk IDs, and candidate evidence policy belong to schemas. Alternative: expose document paths/records. Rejected because consumers would bypass ownership and storage boundaries.

### 4. Enforce completed boundaries and remove only their exceptions

Architecture tests reject document internals in synchronous discovery, KB/association persistence in registry/resolution/definition adapters, mutable persistence values in public snapshots, document extraction dependencies on schema implementations, and all production feature-to-bootstrap dependencies. Integration adapters use public values only and cannot add transactions or policy.

Remove step-4 exception pairs for synchronous discovery input access and document schema resolver access. Retain exact pairs for durable draft imports (step 6), evaluation/publication (step 7), search legacy entry points (step 8), final reprocessing organization and remaining support/assembly coupling (step 9). Keep earlier reprocessing boundaries enforced; this slice does not permit documents internals merely because a schema service is still outside the final package layout.

Alternative: migrate every schema workflow at once. Rejected because drafts have distinct durable claims, sources, evaluation, and publication guarantees.

## Risks / Trade-offs

- Schema status changes during definition/association separation -> check global versus KB listing, active-reference guards, shared-schema activation, and same-target idempotence.
- Association locking loses transactional participation -> persistence tests for rollback and competing activation; no transaction boundaries in integration adapters.
- Snapshot immutability or hash mapping is incomplete -> mutation tests and exact-content/hash expected-target tests, including same identity with changed inactive content.
- Input abstraction changes limits or errors -> ordered mixed-source tests, oversized bytes before parsing, foreign/missing equivalence, and no model calls before full validation.
- Legacy consumers force record exports -> conversion only in exact named legacy bridges with no new callers and explicit retirement.

## Migration Plan

1. Add immutable schema snapshots and KB admission/association ports/capabilities; split association persistence ownership while retaining existing table mappings and transaction behavior.
2. Move registry/parser/validator/resolution behind public contracts, adapt controller mapping, and switch document extraction. Thin legacy entry points delegate to the same resolution.
3. Add discovery input ports and document capabilities, preserve validation ordering, then move discovery workflow/collaborators and map reusable draft imports without migrating draft policy.
4. Enforce architecture and remove completed step-4 exceptions; verify predecessors still pass.
5. Run the fast suite, focused registry/discovery/association and document extraction/reprocessing integration coverage, and canonical end-to-end flow with escalated execution for Docker/Testcontainers.
6. Update architecture/schema portal pages, roadmap status/link, and overlapping README/AGENTS/CLAUDE facts; run documentation alignment and site generation.

Deploy in the same application with unchanged SQL, binary locations, schema hashes, and durable snapshots. Rollback reverts contracts/packages/wiring and retains data. If this change is implemented independently before step 4, map input capabilities to current document implementations and preserve exact exception scope; in the agreed sequence, use step 4's final ownership packages.
