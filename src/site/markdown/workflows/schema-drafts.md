# Schema drafts

Schema drafts are knowledge-base-owned planning resources. They retain source snapshots, analyses, evidence, review decisions, conflicts, diffs, evaluations, and publication provenance without changing the active schema.

All routes start with `/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts`.

```mermaid
flowchart LR
    D[OPEN draft] --> S[Collect DOCUMENT / FILE / TEXT sources]
    S --> A[Durable analysis]
    A --> R[Candidates, decisions, conflicts, projection and diff]
    R --> E[Held-out evaluation]
    E --> P[Publish exact revision/hash]
    P --> I[Inactive registered schema]
    I --> X[Explicit activation]
    X --> Q[Optional reprocessing plan]
```

## Create and collect sources

```json
{
  "targetName": "legal-contracts",
  "targetVersion": 2,
  "baseSchemaId": "<optional-schema-id>",
  "guidance": {
    "additionalInstructions": "Prefer stable business identifiers",
    "guidance": {
      "domainDescription": "Supplier agreements",
      "intendedQuestions": ["When may either party terminate?"]
    }
  }
}
```

With a base schema, names must match and the target version must increase. Every mutation supplies the current draft `revision`; stale revisions return HTTP 409.

Sources are explicit:

- `DOCUMENT` references an owned document and snapshots its SHA-256 without copying its binary.
- `FILE` stores a multipart binary in the draft-owned namespace and creates no document resource.
- `TEXT` stores pasted content in the draft-owned namespace; list responses return metadata, not content.

Duplicate active content returns the existing source. A replaced document source becomes `STALE`; a deleted document becomes `UNAVAILABLE`; refresh explicitly advances the source revision. Removing analyzed sources is logical so evidence remains auditable, while never-analyzed draft-owned content can be physically removed. Restore reactivates eligible removed sources.

Open-draft deletion requires the current revision and no running analysis. It deletes only draft-owned artifacts and metadata, never referenced documents or registered schemas. Published drafts are retained as read-only audit records.

## Analyze and poll

Start `POST /{draftId}/analysis-runs` with the current revision. HTTP 202 includes a `runId` and `Location`; poll until `COMPLETED`, `PARTIAL`, or `FAILED`. Only one run reserves a draft at a time, and bounded queue overload is rejected before leaving a durable orphan.

A run snapshots the draft/guidance revisions, source membership and hashes, profile ID/revision, prompt/candidate contract revisions, and settings. Source results commit independently. Exact matching successes can be reused; retry schedules unresolved sources and links the new run to its predecessor. Startup recovery closes leftover `RUNNING` work as retryable failure while preserving successes.

`retryable` is persisted failure classification. `canRetry` is the current action hint and also requires a terminal run, open draft, active sources, and no current analysis. A stale result remains in history but becomes current only when the draft revision and membership still match its snapshot.

## Review candidates and conflicts

Candidates keep evidence origins (`OBSERVED`, `GUIDED`, `INFERRED`, `EXISTING`) separate from append-only decisions (`ACCEPT`, `REJECT`, `MODIFY`, `PIN`). Base elements are `EXISTING`; no observed evidence is fabricated.

Exact-coordinate decisions are reapplied after analysis. Missing evidence or disagreement with a pin creates a conflict. Resolve it by selecting a retained alternative or providing a custom definition. The effective projection combines inherited elements, aggregate candidates, decisions, and resolutions. Stable diffs classify changes as `ADDITIVE`, `REVIEW_REQUIRED`, or `BREAKING` against the base schema (or prior aggregate when no base exists).

## Held-out evaluation

Evaluation documents must be outside the current discovery evidence set by exact binary SHA-256. Eligibility exposes draft-wide `READY`/`NOT_READY`; `DRAFT_ANALYSIS_REQUIRED` blocks selection and `ACTIVE_DISCOVERY_EVIDENCE` marks excluded documents.

Start an evaluation with revision, document IDs, and optional advisory analysis. Dry evaluation parses, chunks, extracts, and validates in memory—no chunks, embeddings, extraction runs, graph facts, or relationships are written. Deterministic metrics include recognized entity rate, dropped relationship rate, key availability, property type conflicts, missing required properties, low-support candidates, and guided-without-evidence candidates. Zero denominators are `NOT_APPLICABLE` with a null rate.

## Publish, activate, and reprocess

Read publication readiness for the exact draft revision. Publishing requires its canonical `projectionContentHash`; any stale revision/hash is rejected. Publication creates and associates one normal `INACTIVE` generated schema and records its original hash. Later inactive edits are allowed by normal registry rules and are reported as content drift.

Publication does not activate and does not process documents. Activate the published schema with the registry endpoint. Then create an explicit reprocessing plan if existing documents need overwrite processing. The plan snapshots schema/hash, AI profile revision, document hashes, processing options, and chunker target; items commit independently. Source changes become `STALE_SOURCE`, schema/hash changes block queued work, and retry uses explicit `RESNAPSHOT_UNRESOLVED` semantics.

## Privacy and implementation

Application logs contain identifiers, revisions, fingerprints, counts, status, timing, and exception class—not source text, guidance, prompts, candidates, model responses, or schema projections. Controlled trace content follows [AI observability settings](../operations/observability.md).

Draft authoring lives under `schemas.drafts`; evaluation, publication, and
reprocessing own their API values, workflows, checkpoints, and persistence under
`schemas.evaluation`, `schemas.publication`, and `schemas.reprocessing`. Authoring
admission, review/projection, contributor fingerprints, and publication linkage
cross immutable internal contracts. Navigation reads bounded batch summaries
from evaluation/reprocessing owners through mapping-only adapters, preserving
history ordering, currentness, and pagination without downstream repository reads.

Document-owned preparation loads/parses/chunks the source, and per-chunk dry
extraction returns raw and validated observations for evaluation-owned metrics.
The existing outcome order remains source/hash check, reusable outcome lookup,
client availability, preparation, extraction/validation, metrics, and checkpoint.
Live chunking/profile resolution, contributor fallback, canonical snapshots and
reuse fingerprints, and source-race behavior remain compatible. Advisory analysis
retains its deterministic `COMPLETED_WITHOUT_MODEL_JUDGMENT` fallback; this
ownership change adds no advisory model adapter.

Publication persists its intent before registry registration. Resume reconciles
the same associated name/version/content rather than creating another schema;
completion commits publication and draft-owned linkage together. Those relational
checkpoints remain separate from registry registration and external processing.
The existing readiness order, revision/hash guards, idempotency, content-drift
reporting, inactive publication, and explicit activation behavior are unchanged.

Implementation: `schemas.drafts.api.SchemaDraftController`,
`schemas.evaluation.api.SchemaDraftEvaluationController`,
`schemas.publication.api.SchemaDraftPublicationController`, and
`schemas.reprocessing.api.SchemaReprocessingPlanController`, with owned application,
domain, ports, and adapter packages. See the [architecture boundary](../concepts/architecture.md#schema-evaluation-and-publication-boundary)
and Swagger/OpenAPI for the full route and paged DTO catalog.
