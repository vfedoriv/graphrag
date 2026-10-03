## Context

See [proposal.md](proposal.md) for motivation and
[the roadmap](../../../../docs/MODULARIZATION_DESIGN.md) for the target architecture.
This is the first implementation slice; package names below are selected for new
boundary code, not a requirement to relocate every legacy class.

`SchemaReprocessingPlanService.processItem` claims an item, loads the current
document, checks its SHA-256, constructs processing input, invokes overwrite
processing, and translates its result into an item outcome. Chunk migration
reconstructs options and chunking context from `ChunkMigrationSnapshot`.
`SchemaReprocessingRecoveryService.completedOverwrite` reads processing runs.

Current recovery recognizes an active completed run with the item's source hash,
the effective chunker revision for chunk migrations, and a start time no earlier
than the item's start when present. It does not check additional schema/profile
fields. Preserve that predicate rather than silently strengthening it.

Relevant behavior remains defined by `schema-reprocessing-plans`,
`chunk-strategy-reprocessing`, `application-workflow-orchestration`, and
`document-processing-run-history`. The delta adds architectural governance only.

## Goals / Non-Goals

**Goals:** Establish a testable execution/outcome boundary for both plan reasons;
retain schema ownership of plan claims and decisions; avoid entity/provider-client
leakage and reverse source dependencies.

**Non-Goals:** Preparation/classification migration, changing stale-source race
semantics, stronger recovery matching, remote execution, persisted snapshot
changes, feature-wide relocation, or new processing algorithms.

## Decisions

### 1. Consumer ports and document capabilities have different owners

Add `schemas.reprocessing.ports.ReprocessingDocumentExecutor` and
`ReprocessingProcessingOutcomeReader`. Their immutable requests/results belong
to schemas and contain only the values the caller needs. Add document-owned
`documents.contracts.DocumentReprocessing` and
`DocumentProcessingOutcomes` capabilities, backed by facades under
`documents.application.processing`.

Add integration adapters under `bootstrap.integration.reprocessing` to map
schema requests to document requests and results back. Features do not depend on
bootstrap. Keep the bridge free of repository calls, plan state, classification,
chunk reconstruction, and model invocation. New document facades may delegate
to existing legacy services until document consolidation.

The execution port and document capability expose a separate `sourceMatches`
operation with an immutable knowledge-base/document/hash scope. The schema caller
invokes it after claiming the item and before decoding the target, outside the
item processing failure handler. Only a matching source proceeds to target
assembly and execution. This staged capability preserves stale-source precedence
over malformed target data and leaves source-lookup failures as interrupted
claims for recovery. Execution does not repeat the source lookup, preserving the
existing check-to-processing race without adding locks or transactions.

Alternative: schemas directly imports the documents API. Rejected here because
documents already needs schema resolution and later build-module separation
would inherit that cycle. A global contracts module is unnecessary for this slice.

### 2. Isolate the migrated caller so architecture tests are enforceable

Extract the document interaction from `processItem` into a small schema-owned
`schemas.reprocessing.application.ReprocessingItemExecution` collaborator.
Plan/item claims, checkpointing, counts, and completion remain in the existing
orchestrator. The collaborator depends on the execution port only and returns a
typed result. The orchestrator builds its request from persisted plan/item values.

Migrate recovery to the outcome-reader port and remove its processing-run
repository dependency. This produces whole-class boundaries that architecture
tests can check. Do not try to exempt an entire service package because the
remaining orchestrator still has preparation dependencies.

### 3. Requests carry durable values rather than runtime objects

An execution request includes knowledge-base/document IDs, expected source hash,
plan reason, and the profile scope currently used for execution. Represent
ordinary schema-activation processing and immutable chunk-migration processing
as closed distinct target variants.

The activation variant preserves the existing requested processing-option bag;
document option resolution remains authoritative. The migration variant contains
profile ID/revision, embedding-space ID, schema ID/hash, saved chunk target, and
the per-document parser/options/revision target. Use immutable value records with
defensive collection copies; exclude `AiProfileNode`, `DocumentUploadNode`,
`ChunkingContext`, model clients, token estimators, and secrets.

Schema snapshot decoding stays schema-owned. The integration adapter performs
field mapping into equivalent document-owned boundary values. It must not pass
`ChunkMigrationSnapshot` itself into the documents API or make document contracts
import schemas implementation types. Existing persisted JSON is unchanged.

The document facade resolves the profile and reconstructs runtime options/context
using existing services, then calls `DocumentProcessingService`. This moves the
existing execution-input assembly, not the plan-creation snapshot algorithm.

### 4. Keep outcomes and failure semantics explicit

The document facade performs the existing source lookup/hash comparison before
target decoding through `sourceMatches`. Missing/replaced sources cause schema
orchestration to complete `STALE_SOURCE` without processing. Source-lookup
exceptions propagate outside the item processing failure handler, as before.
Completed processing maps to `SUCCEEDED`; non-completed processing maps to the
existing failed outcome. Exceptions preserve the originating exception class
for the existing schema-side privacy-safe failure mapping. Do not convert every
failure into a generic wrapper class or expose exception messages/content.

Schema-owned orchestration remains responsible for conditional item completion,
retryability, and logging. Source validation remains at the existing relative
point in the flow; this slice does not introduce a stronger transactional lock
around a source check and the subsequent processing call.

Outcome lookup accepts document scope, expected source hash, optional required
chunker revision, and the item's optional start time. The documents capability
returns whether the current existing recovery predicate matches. It reads owned
run state; the schemas workflow repairs claims/counters and decides retry status.

### 5. Preserve store-specific checkpoints and profile scope

The integration adapter introduces no transaction. Keep item claims/completion
and document run checkpoints in their existing relational boundaries; do not
wrap model/filesystem/graph work in an encompassing transaction. Keep profile
context entry/cleanup around processing so both successful and failing calls
retain the existing profile selection behavior.

Preparation exceptions are frozen as explicit dependencies in the legacy
orchestrator. The new execution collaborator, schema ports, recovery path, and
integration adapters receive strict rules immediately. New documents facades are
named transitional bridges to legacy processing implementation; broad feature
relocation belongs to roadmap change 4.
The orchestrator's `processItem` and `executionTarget` also receive method-origin
checks so preparation exceptions cannot be reused by execution in the same class.

## Risks / Trade-offs

- Contract duplication at the integration seam -> small field mappings and mapping
  tests; avoid copied business decisions.
- Misclassifying failures or source changes -> retain existing status codes,
  exception-class categories, and order of operations in focused tests.
- False recovery completion -> preserve hash/revision/start-time/active-completed
  matching exactly, including historical schema-activation behavior.
- Partial architecture enforcement -> isolate migrated execution as a dedicated
  class and freeze preparation edges explicitly rather than allowlisting a package.
- Persisted target drift -> map existing snapshot fields without reserializing or
  changing historical JSON shapes.

## Migration Plan

1. Add schema ports, document public values/capabilities, facades, and integration
   adapters while the old path still compiles.
2. Switch execution for both plan reasons and outcome inspection; remove migrated
   dependencies and add the scoped architecture rules.
3. Verify existing processing, stale-source, target-change, retry, and recovery
   behavior alongside port/facade/adapter tests and persistence integration tests.
4. Update implemented architecture facts in the portal and shared guidance.

Deploy as one application with no SQL or persisted JSON migration. Rollback is an
application code rollback; records written during this slice remain readable by
the prior application. Apply change 2 only after this change is verified; both
edit the reprocessing orchestrator and should not be implemented concurrently.
