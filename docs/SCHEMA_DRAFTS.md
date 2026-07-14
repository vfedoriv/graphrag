# Persistent schema drafts

Schema drafts are knowledge-base-owned planning resources. They retain source snapshots, analysis history,
evidence, review decisions, conflicts, and diffs without registering or activating a schema. A draft remains
editable while its status is `OPEN`; a future publication workflow may turn it into a read-only audit record.

## Lifecycle

All endpoints use the prefix:

`/api/v1/knowledge-bases/{knowledgeBaseId}/schema-drafts`

Create a draft with a target `name + version`, optional associated base schema, and optional guidance. When a
base is supplied, its name must match and the target version must be greater. Every mutation supplies the
current draft `revision`; stale revisions return RFC 7807 HTTP 409 responses.

Draft sources are explicit:

- `DOCUMENT` stores an owned document ID and SHA-256 snapshot without copying its binary.
- `FILE` stores the multipart binary under the draft-owned storage namespace and creates no `DocumentUpload`.
- `TEXT` stores pasted text under the same draft-owned namespace. List responses contain metadata only.

Duplicate active content of the same source type returns the existing source. Replaced documents become
`STALE`, deleted documents become `UNAVAILABLE`, and a document refresh explicitly advances the source
revision. Removing an analyzed source is logical so historical evidence remains auditable; removing an
unanalysed source deletes its draft-owned content.

Open-draft deletion requires the current revision and no running analysis. It removes draft-owned artifacts
and metadata but never deletes referenced documents or registered schemas. Published draft records cannot be
deleted through this operation.

## Analysis and polling

Start analysis by posting the current revision to `/{draftId}/analysis-runs`. The API returns HTTP 202, a
durable `runId`, and a `Location` header. Poll that location until the run is `COMPLETED`, `PARTIAL`, or
`FAILED`. Per-source outcomes are paged with `page` and `size` query parameters.

Only one run can reserve a draft at a time. The bounded worker queue rejects overload before leaving a run
behind. A run snapshots the draft and guidance revisions, active source membership, profile ID/revision,
prompt and candidate contract revisions, and analysis settings. It retains the captured profile client even
if the knowledge-base assignment changes while work is running.

Each source result commits independently. A completed result is reused without a model call only when the
source revision and SHA-256, guidance revision/fingerprint, profile ID/revision, prompt and candidate
revisions, and settings fingerprint all match. Retry creates a new durable run, reuses matching successes,
and schedules unresolved sources. Startup recovery closes leftover `RUNNING` runs as retryable failures while
preserving completed source results.

An aggregate becomes current only if the draft revision and active source membership still match the captured
snapshot. Results from a stale run remain auditable but cannot replace the current aggregate.

## Review and diffs

Candidates preserve evidence origins (`OBSERVED`, `GUIDED`, `INFERRED`, `EXISTING`) separately from append-only
review decisions (`ACCEPT`, `REJECT`, `MODIFY`, `PIN`). Base-schema elements are materialized as `EXISTING`
without fabricated observed evidence. Exact-coordinate decisions are reapplied after reanalysis; missing
evidence or disagreement with a pin creates an explicit conflict.

Conflict resolution selects a retained alternative or supplies a custom definition. The effective projection
combines inherited elements, the current aggregate, decisions, and resolved conflicts. Diff output is stable
by coordinate and classifies changes as `ADDITIVE`, `REVIEW_REQUIRED`, or `BREAKING` against the associated
base schema, or against the prior aggregate when no base exists.

Normal application logs remain metadata-first: identifiers, revisions, fingerprints, counts, statuses,
timings, and exception classes are allowed; source text, guidance, prompts, candidates, model responses, and
schema projections are not. Model content is controlled only by the existing AI observation capture settings.
