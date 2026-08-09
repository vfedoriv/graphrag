## Context

The backend has two legitimate processing outputs. `fixed-character` produces persisted `CHILD` chunks without parents; `recursive` produces `PARENT` chunks and parented `CHILD` chunks. `DocumentProcessingService` snapshots one strategy per run, replacement deletes the previous document chunk population, and `DocumentChunkPersistenceAdapter` already rejects a hierarchy batch containing an unparented child.

The current read specification nevertheless guarantees navigation for a third, mixed topology. Within this repository, that state is constructed only by integration fixtures that write directly to Neo4j; direct repository bypasses, manual imports, and other out-of-band graph writes can also create it because Neo4j has no document-level topology constraint. A read-only audit on 2026-08-09 found four document populations, all pure hierarchy, with zero unparented children, zero null or unsupported kinds, and no invalid parent, run, or revision references.

## Goals / Non-Goals

**Goals:**

- Make `EMPTY`, `FLAT`, and `HIERARCHICAL` the only valid document-scoped chunk topologies.
- Reject mixed writes before deleting or replacing valid persisted chunks.
- Reject invalid bounded collection reads as a stable integrity conflict.
- Preserve fixed-character FLAT paging, hierarchy flat counts, response `kind=CHILD`, ownership isolation, and deterministic ordering.
- Provide an explicit pre-deployment audit and focused regression coverage.

**Non-Goals:**

- Remove `kind=FLAT`, `flatChunkCount`, fixed-character processing, or direct owned chunk lookup.
- Migrate, repair, or preserve legacy or manually imported invalid topology data.
- Change the persisted `PARENT` and `CHILD` kinds or introduce a stored `FLAT` kind.
- Change chunking strategy selection or automatic reprocessing behavior.

## Decisions

### Model topology as a document-level invariant

Classify a document snapshot as `EMPTY` when it has no chunks, `FLAT` when it has no parents and every child is unparented, and `HIERARCHICAL` when it has at least one parent and every child has a valid same-scope parent. Any combination of parents and unparented children, unsupported kinds, orphan references, or cross-run/revision references is invalid.

This keeps FLAT as a virtual query selector rather than a persisted kind. Alternatives considered: removing FLAT would break fixed-character documents; retaining mixed as a fourth topology would preserve an unreachable product state.

### Validate replacement input before destructive mutation

Extend the persistence adapter's pre-save validation into an explicit topology validator and run it before `deleteByDocumentId`. Pure-flat batches remain valid when no parents exist. Hierarchical batches require every `CHILD` to reference a contained parent with matching knowledge base, document, processing run, and effective revision.

Alternatives considered: validating only after save risks deleting valid prior output before discovering an invalid replacement; relying only on the processing strategy leaves repository callers able to bypass the invariant.

### Return a stable integrity conflict for invalid collection reads

Bounded page and hierarchy services will classify the owned document snapshot before returning collection data. An invalid topology returns RFC 7807 `409 Conflict` with stable detail `Document chunk topology is invalid`. Valid pure-flat and pure-hierarchy responses retain their current schemas and filters. Direct owned lookup remains available for operational diagnosis but does not make invalid collection navigation supported.

Alternatives considered: silently serving whichever predicate was requested perpetuates invalid-topology support; a generic `500` obscures a known persisted-state conflict; startup failure would make one bad document unavailable at the cost of the entire service.

### Treat the rollout audit as a deployment precondition

Add a read-only Neo4j audit query/test that reports per-document parent, parented-child, unparented-child, null-kind, and unsupported-kind counts plus invalid hierarchy relationships, scopes, runs, and revisions. Deployment proceeds only when no row classifies as invalid. The audit is diagnostic and does not mutate data.

Alternatives considered: an automatic cleanup could destroy manually imported data and is explicitly out of scope; omitting the audit would turn an assumed invariant into an unverified rollout risk.

## Risks / Trade-offs

- [A hidden mixed document becomes unreadable through bounded collections] → Run the raw graph audit before deployment and stop rollout if any invalid row exists.
- [Topology classification adds graph work to bounded reads] → Use one document-scoped aggregate query and retain existing bounded content queries; cover query counts and indexes in integration tests.
- [Pure-flat children are rejected accidentally] → Test fixed-character persistence and bounded FLAT reads with no parents.
- [Replacement validation deletes valid data before rejecting] → Assert validation occurs before `deleteByDocumentId` with adapter unit tests.
- [Frontend behavior changes independently] → Keep this backend change limited to the backend contract; frontend changes are handled separately.

## Migration Plan

1. Run and record the read-only Neo4j topology audit; require zero invalid documents.
2. Add topology classification, pre-delete write validation, and bounded-read conflict handling.
3. Replace mixed controller/repository fixtures with separate pure-flat, pure-hierarchy, and invalid-topology rejection fixtures.
4. Run backend quality gates and live-smoke both recursive and fixed-character documents.
5. Deploy the backend. Frontend changes are handled separately.

Rollback reverts the service and specification changes. No persisted data is transformed, so valid pure-flat and pure-hierarchy documents remain compatible with the previous release.

## Open Questions

None.
