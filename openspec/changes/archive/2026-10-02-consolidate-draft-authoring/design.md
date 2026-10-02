## Context

See [proposal.md](proposal.md), [the roadmap](../../../docs/MODULARIZATION_DESIGN.md), and the [architecture delta](specs/architecture-boundary-governance/spec.md). Steps 1–5 are implemented. Registry and synchronous discovery now live under `schemas.registry` and `schemas.discovery`; documents exposes scoped source input and parsing, and registry exposes immutable active/expected snapshots. Durable draft classes remain spread across `controller`, `dto`, `service`, `domain`, `repository`, and relational infrastructure packages.

The exact step-6 exceptions in `ArchitectureBoundaryTest` show direct draft-to-document and draft-to-registry implementation dependencies. `SchemaDraftSourceService` reads document records for source snapshots. `SchemaDraftAnalysisSourceFactory` reads records/binaries and invokes the document parser. Lifecycle, review, and navigation read registry records and repositories. Navigation also batches evaluation/reprocessing summaries, so moving draft ownership must not turn those later-slice reads into per-draft queries. `SchemaDraftAnalysisService` coordinates durable claims, bounded concurrent source work, deadlines, checkpoints, reuse, and aggregate promotion; it is a relocation target, not a workflow rewrite.

## Goals / Non-Goals

**Goals:** Establish one schema-owned authoring area, remove its foreign implementation access, preserve behavior and historical state, and make the completed boundary fail on new violations.

**Non-Goals:** Redesign draft APIs, analysis algorithms or persistence; move held-out evaluation, publication, or reprocessing orchestration; split the application or add transport boundaries; change the synchronous discovery workflow from step 5.

## Decisions

### 1. Group by authoring responsibility while leaving downstream workflows in place

Use `schemas.drafts` for authoring API mapping, application workflows, deterministic rules/values, owned ports, and relational/binary adapters. Move lifecycle, sources, source storage mutation/reconciliation, analysis and recovery, review/conflicts, draft navigation, their records, and repositories together. Keep the existing SQL names, columns, constraints, entity/query names, serialized payloads, binary namespace, and checkpoint transaction annotations. Move concrete helpers without creating interfaces solely for package symmetry.

Evaluation/publication and reprocessing stay in their current implementation areas for steps 7/9. Where they need authoring facts, use narrow schema-owned read surfaces or temporarily retain exact internal schema dependencies recorded for the later slice; do not export repositories as public contracts. Draft navigation may continue to compose bounded downstream summary queries within schemas until those owners move, then replace the exact seam in step 7. Preserve one batch path for list responses.

Alternative: move every draft-related service and repository at once. Rejected because held-out evaluation and publication have separate document effects and readiness policy assigned to step 7.

### 2. Keep document state and parsing behind scoped capabilities

Add schemas-drafts consumer ports for (a) owned document metadata/fingerprint inspection and (b) content plus parsing needed for analysis. Implement them in `bootstrap.integration.schemas` using documents public capabilities. Reuse `DocumentSourceInputs.readOwned` and `parse` for content preparation; add a narrowly scoped document metadata capability if needed to preserve source-add/refresh fingerprint reads without forcing binary parsing or loading. The provider checks the knowledge-base/document scope and preserves the existing missing-versus-foreign behavior. Values carry only IDs, source hash, name/content type, bytes when required, and perhaps size; bytes are defensively copied. No content URI, local path, mutable record, or parser crosses the boundary.

Schemas remains responsible for source revision and SHA snapshot comparisons, `STALE`/`UNAVAILABLE` classification, draft-owned file/text storage, trim/blank checks, character/chunk limits, stable analysis chunk IDs, and error precedence. Keep the existing sequence of fingerprint comparison, binary read, parse, and analysis bounds; the step-5 synchronous discovery port must not silently become a draft source workflow or persist draft input. Draft-owned binaries continue through shared binary primitives behind a schema-owned adapter and journal. Referenced document binaries are never copied into draft storage.

Alternative: expose `DocumentUploadNode` or a broad upload service to drafts. Rejected because it preserves the foreign ownership breach. Alternative: have documents build draft analysis chunks. Rejected because draft revisions, limits, and chunk IDs belong to schemas.

### 3. Use registry and knowledge-base facts without changing draft admission

Introduce purpose-specific schemas-drafts ports for base schema lookup/association and managed knowledge-base plus active AI profile identity/revision. Implement through registry/knowledge-base public capabilities and mapping-only adapters. Reuse immutable `SchemaSnapshot` for stored definition/hash when it covers a lookup; add a scoped by-ID snapshot/read method only where draft base validation or review truly needs it. Preserve base name/version/association validation, exact stored content hash for diff baselines, and the distinction between global and knowledge-base active status. Keep profile identity/revision in durable run snapshots; model client construction remains AI-owned.

Draft authoring may reuse `schemas.discovery` candidate and aggregation collaborators through a deliberate schema-internal contract. Keep deterministic candidate shapes and source failure classification shared rather than copying algorithms. Avoid moving synchronous discovery ownership into drafts or exporting its implementation through public cross-feature contracts. Split the mixed draft DTO class only as needed to place authoring API mapping under the owner while preserving JSON, validation, OpenAPI, and HTTP paths.

Alternative: keep direct registry repository access because both are under schemas. Rejected because it erases the registry boundary established in step 5. Alternative: recreate a second draft-specific schema parser. Rejected because validation would drift.

### 4. Preserve checkpoints and make exception retirement exact

Keep relational claim and optimistic revision updates in their current transaction scope. The analysis worker still reads the captured source/profile/settings snapshot, records accepted per-source outcomes independently, rejects late results, promotes only a current aggregate, and recovers stale claims. Source storage intent still commits before filesystem work, with idempotent reconciliation afterward. Mapping adapters add no transaction annotation, retry, or policy and join caller transactions for synchronous reads.

Update `ArchitectureBoundaryTest` from the new package layout. Remove every step-6 entry in `FROZEN_DOCUMENT_EDGES` and `FROZEN_SCHEMA_BRIDGE_EDGES`, plus any newly inventoried authoring-to-knowledge-base implementation exceptions. Keep exact later-step pairs and tests that fail on new or stale entries. Enforce that foreign features and bootstrap do not reverse the dependency into authoring internals. Do not interpret a package move as permission for a broader exception.

Alternative: relax the architecture test for all legacy schema services during relocation. Rejected because later steps would gain untracked implementation access.

## Risks / Trade-offs

- Package moves change JPA discovery, repository scanning, query entity names, or stored JSON type handling → check explicit mappings and historical read fixtures, then run focused relational integration tests.
- Source capability changes alter stale/unavailable status or error order → test missing, foreign, replaced, deleted, and binary-unavailable references at add, refresh, and analysis time.
- Contract reads alter transaction participation or list performance → test rollback/currentness and query bounds; keep adapters synchronous and transaction-free.
- Worker relocation changes Spring proxy calls or claim/checkpoint ordering → verify concurrent claim, late completion, partial outcomes, retry lineage, and stale-run recovery with existing tests.
- Shared discovery collaborators acquire a new draft-specific branch → keep preparation/aggregation contracts explicit and verify synchronous discovery outputs still match.
- Navigation crosses the step-7 seam → freeze its exact downstream summary dependencies and retain bounded batch reads until evaluation/publication migration.

## Migration Plan

1. Establish schema-drafts package ownership and move draft records, repositories, storage adapters, lifecycle/source/review helpers, and API mapping while preserving mappings and public response shapes.
2. Add document, registry, knowledge-base, and AI-profile fact ports/capabilities where existing public methods are insufficient. Wire mapping-only integration adapters; migrate authoring callers and remove direct foreign records/services.
3. Relocate durable analysis, source preparation, recovery, and navigation without changing claim, deadline, retry, aggregation, or checkpoint algorithms. Keep downstream summary reads bounded and explicitly transitional.
4. Retire exact step-6 architecture exceptions and add enforcement for the new owner. Run deterministic and focused persistence tests, including document snapshot behavior, source storage recovery, analysis claims/currentness, and draft review/diff.
5. Run the fast suite and canonical end-to-end flow; use escalated execution for Docker/Testcontainers integration tests. Update portal and shared contributor facts; run documentation alignment and site generation, then validate this OpenSpec change.

Deploy as the same application with no SQL or binary migration. Rollback reverts code and wiring while keeping persisted draft history readable by the previous version.
