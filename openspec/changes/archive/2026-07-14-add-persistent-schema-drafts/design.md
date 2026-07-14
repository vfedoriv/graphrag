## Context

The multi-source discovery change establishes typed source candidates and deterministic aggregation, but its request-scoped results cannot support incremental evolution, durable retry, or user decisions. This change introduces a persistent planning domain alongside existing knowledge-base documents and registered schemas. A draft is evidence and review state, never an extraction contract.

The repository already persists operational runs and graph entities in Neo4j, stores document binaries outside Neo4j with reconciliation metadata, routes AI clients by knowledge-base profile, and applies optimistic `@Version` fields. The draft design follows these patterns while adding a bounded in-process background executor because no general job platform exists today.

## Goals / Non-Goals

**Goals:**

- Persist drafts, source snapshots, analysis progress, candidates, conflicts, decisions, and aggregate revisions.
- Add sources incrementally without reanalyzing unchanged work.
- Support retry after per-source failure or process interruption.
- Preserve user modifications and pins across reanalysis.
- Keep direct draft uploads separate from normal document ingestion.
- Provide deterministic diffs against a base schema and prior aggregate.

**Non-Goals:**

- Evaluating held-out documents, publishing schemas, activating schemas, or reprocessing documents.
- Converting draft files into normal documents automatically.
- Automatically accepting even high-confidence additive candidates.
- Automatically resolving semantic aliases or breaking changes.
- Introducing a distributed queue or multi-node job scheduler in the first implementation.

## Decisions

### Model drafts as a separate Neo4j subgraph

Use a `SchemaDraft` root linked to its `KnowledgeBase`, optional base `SchemaDefinition`, `SchemaDraftSource` nodes, `SchemaDraftAnalysisRun` and per-source result nodes, immutable aggregate revisions, review decisions, and conflicts. Large structured payloads may be stored as validated JSON properties where independent graph traversal is not required; ownership and lifecycle entities remain explicit nodes.

Every mutable root and run entity uses optimistic versioning. Services enforce knowledge-base ownership before returning any draft child resource.

Alternative considered: store one serialized draft document. Rejected because source/run lifecycle, retry queries, optimistic mutations, and cleanup need independently addressable state.

### Give draft-owned content its own storage namespace

Direct files and pasted text are stored by a draft source storage collaborator backed by `BinaryStorageService`, under draft/source identifiers rather than normal document identifiers. Storage mutation tracking mirrors document upload reconciliation. Existing document references store only document ID and SHA-256 snapshot.

Analyzed source removal is logical so evidence remains auditable; unanalysed draft-owned content can be physically removed. Open draft deletion removes all draft-owned content. Published draft retention is specified by the later publication change.

Alternative considered: create `DocumentUpload` records for direct files. Rejected because schema-design samples must not silently enter normal ingestion, listing, deduplication, or processing workflows.

### Snapshot every input that affects model output

A source reuse key contains draft ID, source ID/revision and content SHA-256, guidance revision/fingerprint, AI profile ID/revision, prompt contract revision, parser/chunking settings fingerprint, and candidate contract revision. Completed results are reused only on exact matches. Reuse is scoped to a draft to keep authorization and evidence identity simple.

The AI client is resolved at run creation and retained by the in-memory work item, while the identifying profile revision is persisted. A restarted process does not attempt to recreate an in-flight old-revision client; it closes the run as interrupted and a retry uses the current profile in a new snapshot.

Alternative considered: cache by document ID. Rejected because normal document replacement preserves ID while changing content.

### Use durable runs over a bounded in-process executor

Starting analysis commits the run snapshot before work is submitted and returns HTTP 202. Only one analysis run mutates a draft at a time. Each source result commits independently. On startup, leftover `RUNNING` runs are marked failed/interrupted; retry reuses completed per-source results and schedules only unresolved work.

Run terminal status is `COMPLETED`, `PARTIAL`, or `FAILED`. Whether the result is current is a separate flag based on matching draft/source revisions, avoiding extra terminal statuses. The executor has typed concurrency and queue limits; overload is rejected before a run is created.

Alternative considered: Spring `@Async` without durable run records. Rejected because it cannot provide reliable progress, restart recovery, idempotency, or per-source retry.

### Keep immutable aggregate revisions and append-only decisions

Every successful or partial run produces an immutable deterministic aggregate revision. Decisions are append-only events with a current effective view. Reanalysis first builds a new evidence aggregate, then reapplies compatible accepted, modified, pinned, and rejected decisions by canonical candidate identity. If coordinates changed or evidence conflicts with a pinned definition, the system creates a review conflict rather than guessing.

Base-schema elements enter as `EXISTING` and effective. Removal, rename, key replacement, direction change, or incompatible type change requires an explicit breaking decision.

Alternative considered: edit candidate rows in place. Rejected because it destroys evidence history and makes reanalysis reconciliation ambiguous.

### Separate origin, review state, and compatibility

Origins describe evidence (`OBSERVED`, `GUIDED`, `INFERRED`, `EXISTING`); review state describes user action (`PENDING`, `ACCEPTED`, `REJECTED`, `MODIFIED`, `PINNED`); compatibility describes the diff (`ADDITIVE`, `REVIEW_REQUIRED`, `BREAKING`). These dimensions are persisted independently because collapsing them would misrepresent guided/observed candidates or imply that high confidence equals approval.

## Risks / Trade-offs

- [In-process jobs do not continue across restart] → Persist per-source progress, close interrupted runs on startup, and make retry reuse completed work.
- [Multiple application replicas could execute the same run] → Use an atomic run claim/lease in Neo4j and initially document single-worker ownership; a distributed queue can replace the executor later without changing APIs.
- [Draft graph volume can grow quickly] → Store immutable result payloads compactly, index ownership/status/reuse keys, paginate candidate/run APIs, and define retention for superseded open-draft results.
- [Pasted text and model evidence are sensitive] → Keep logs metadata-only, apply current trace content controls, and avoid previews in list responses.
- [Reapplying decisions across renamed candidates is ambiguous] → Reapply only exact canonical identities and create explicit conflicts for alias/coordinate changes.
- [Removing analyzed sources cannot immediately reclaim all content] → Make retention visible and clean all draft-owned content on permitted draft deletion; published retention is intentional audit behavior.

## Migration Plan

1. Implement persistent draft roots, ownership checks, repositories, and optimistic revisions.
2. Add draft source storage with mutation reconciliation and document snapshot validation.
3. Add durable runs and the bounded executor, initially disabled or with conservative queue/concurrency defaults.
4. Integrate Phase 1 candidate analysis and aggregation, then add immutable aggregate revisions.
5. Add decisions, conflict resolution, pinning, and diff APIs.
6. Run startup recovery for interrupted analysis runs and Neo4j persistence-version compatibility checks.
7. Roll back by disabling new endpoints and workers; new draft nodes remain isolated from existing extraction/query workflows and can be removed with a dedicated cleanup after rollback.

## Open Questions

None. Evaluation, publication, activation, and reprocessing are covered by the dependent Phase 3 change.
