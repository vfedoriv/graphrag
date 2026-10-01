## Context

See [proposal.md](proposal.md),
[the roadmap](../../../docs/MODULARIZATION_DESIGN.md), and
[change 1's design](../archive/2026-10-01-isolate-reprocessing-execution-recovery/design.md).
Implement change 1 first. This design assumes its schema-owned ports, document
facades, integration mapping, and existing behavior checks.

Remaining document dependencies in `SchemaReprocessingPlanService` include
`selectedDocuments`, `chunkCandidates`, `evaluateChunkMigration`, `isOutdated`,
`createChunkPlan`, retry/history preparation, and the document-specific part of
`targetStillActive`. Today preview classifies all owned documents, then applies
selection and pagination. Creation recomputes the target and verifies the expected
revision before persisting its immutable snapshot. Preserve this order and scope.

Existing `chunk-strategy-reprocessing` and `schema-reprocessing-plans` specs remain
the behavior authority. This delta completes architectural isolation rather than
changing the API or migration algorithm.

## Goals / Non-Goals

**Goals:** Hide document selection data, option/parser resolution, chunk presence,
run revision comparison, and document target construction; keep schema plan policy
and state in schemas; preserve preview/create/retry agreement and durable targets.

**Non-Goals:** New selection policies, query optimizations, a performance rewrite,
global AI compatibility extraction, schema activation ownership changes, new
persisted formats, general discovery/evaluation migration, or remote execution.

## Decisions

### 1. Separate preparation facts from plan policy

Add `schemas.reprocessing.ports.ReprocessingDocumentPreparation` with immutable
consumer requests/results. Extend document public capabilities with a
`documents.contracts.DocumentMigrationPreparation` facade and a small document
selection/read capability. Add mapping under the integration area from change 1.

Documents returns owned document identity/hash/status summaries, chunk presence,
current/outdated classification, effective parser/options targets, and the saved
chunker target. Schemas applies reason-specific selection, counts/pages the preview,
merges blockers, checks the schema target, and persists plans/items/snapshot JSON.

Keep these policy choices in schemas: exactly one activation document choice;
closed chunk selection modes; `OUTDATED_STRATEGY` inclusion rules; expected revision
validation; existing blocker priority; retry lineage; successful-item preservation;
and exclusion of competing destructive plans.

Alternative: return an entirely constructed plan from documents. Rejected because
it would move schema policy and destructive-plan orchestration into documents.
Alternative: expose repositories through another interface. Rejected because it
would retain document implementation knowledge in schemas.

### 2. Preparation is read-only and has one shared algorithm

The document facade uses existing option resolution and chunking snapshot logic.
It classifies no-chunk documents as `NO_CHUNKS`. For documents with chunks,
`CURRENT` requires an active completed processing run matching the current source
hash and effective chunker revision; otherwise the result is `OUTDATED`.

Preparation creates no processing runs, graph artifacts, binaries, or plan rows.
Preview and create call the same document preparation implementation, and creation
recomputes facts rather than trusting preview output. Retry invokes it for the
existing resnapshot policy; successful matching prior items retain current behavior.
Preserve the existing all-owned-document classification scope even for an explicit
selection; optimizing it could change validation/error behavior and is deferred.

Inputs include ownership scope, explicit IDs when applicable, the captured profile
ID/revision or immutable non-secret profile descriptor, and requested processing
options. No provider keys or raw runtime-setting reads cross the boundary. The
document facade continues to use typed existing settings accessors.

### 3. Keep durable target assembly schema-owned

Document preparation outputs typed immutable values. The integration bridge maps
them to schemas-owned values; schemas constructs the existing
`ChunkMigrationSnapshot` and plan fields. Document contracts do not import
schema-owned persisted snapshots or plan entities.

Retain canonical JSON field names, content fingerprints, selection semantics,
chunk/parser/tokenizer/header revisions, profile identity/revision, embedding-space
identity, schema identity/hash, and per-document source hashes/options. Golden
or structural compatibility checks compare representative existing snapshots
with the new mapping and verify restoration through change 1's execution facade.

The ownership of runtime reconstruction remains in documents from change 1.
Preparation and restoration must agree, but do not introduce a second chunker
implementation or a generic JSON blob as the public target API.

### 4. Preserve the current transaction and race guarantees

Integration calls are synchronous and in-process. Document relational reads must
participate in the same relevant relational transaction as current plan creation;
do not introduce `REQUIRES_NEW` or split snapshot preparation across new commits.
Graph reads retain their existing graph transaction behavior. There is no atomic
transaction across stores and no claim that snapshot reads are stronger than today.

Schemas persists the plan/items using the existing checkpoint service and schedules
execution after commit. Keep the existing destructive-plan uniqueness/claim guards
shared between schema activation and chunk migration. A stale expected revision,
foreign/missing explicit document, or blocker creates no plan/items or processing.

The facade reports document-specific target identity and compatibility using the
existing services. Schemas retains schema identity/hash checks and the applicable
profile identity/revision comparisons. Its target-currentness path combines these
with the port's current chunker/embedding target facts; failures retain the current
false/blocked semantics. AI compatibility internals remain deferred to change 3.

### 5. Complete enforcement without migrating unrelated schemas workflows

Remove document/chunk/processing-run repositories, document entities, option
resolvers, and chunking implementation services from reprocessing orchestration.
Its plan/item persistence and schema dependencies remain valid. Extend scoped
architecture tests to all reprocessing preparation/inspection paths and remove
change 1's frozen exceptions. The unrelated draft/discovery dependencies remain
explicitly outside this change rather than receiving new blanket permissions.

## Risks / Trade-offs

- Mapping accidentally changes persisted targets -> compatibility tests for JSON,
  fingerprints, selected hashes, and execution restoration.
- Preview and creation drift -> one preparation algorithm, creation recomputation,
  and tests for a changed revision or blocker between calls.
- Transaction participation changes -> in-process adapters, explicit transaction
  review, and container-backed competing-plan/creation checks.
- Classification scope or blocker order changes -> preserve existing traversal and
  aggregation order; defer query/performance changes.
- Ports become a generic repository API -> purpose-specific summary/target records;
  schemas never receives document or run entities.

## Migration Plan

1. Verify change 1's contracts and adapt this design to their final names without
   changing the ownership model or predecessor semantics.
2. Add preparation/read contracts, document facades, and integration mappings.
3. Switch preview, creation, retry/history selection, and applicable target checks;
   remove legacy preparation access and its exceptions.
4. Run deterministic, snapshot-compatibility, architecture, and persistence checks;
   verify execution/recovery from change 1 still passes.
5. Update implemented architecture and reprocessing documentation.

Deployment remains one application with unchanged schema and persisted formats.
Rollback reverts this slice while retaining change 1. Sequential implementation
is required because both changes edit the orchestrator, contract mappings, and
boundary tests. Existing historical plans must remain readable and executable.
