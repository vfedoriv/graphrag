## Context

Persistent drafts preserve sources, candidates, conflicts, and decisions, but they remain planning artifacts. This change adds quality evaluation and an explicit transition into the established schema/document lifecycle. It must reuse normal schema validation and registration rather than creating a parallel schema format, and it must respect the user's decision that a published inactive schema remains editable under current rules.

Document processing is synchronous today but already records durable stage/run state and preserves the previous successful run on overwrite failure. Reprocessing plans add asynchronous orchestration around that existing single-document operation rather than duplicating parsing, embedding, extraction, or cleanup logic.

## Goals / Non-Goals

**Goals:**

- Evaluate the current reviewed draft against genuinely held-out documents without writing graph artifacts.
- Separate deterministic metric calculations from advisory model judgments.
- Publish exactly one validated inactive schema with atomic retry behavior and an audit link.
- Preserve existing inactive-schema edit/delete and active-schema protection contracts.
- Keep activation and reprocessing explicitly user-triggered.
- Provide durable, retryable, per-document reprocessing progress.

**Non-Goals:**

- Automatically activating a newly published schema.
- Automatically reprocessing documents after publication or activation.
- Treating heuristic LLM evaluation as objective ground truth.
- Locking published inactive schema content.
- Replacing the existing document processing workflow or processing-run model.
- Migrating extracted graph data in place without document reprocessing.

## Decisions

### Evaluate only documents outside the active discovery evidence set

Evaluation requests select existing knowledge-base documents. Eligibility excludes any active source revision contributing evidence to the current aggregate. The run snapshots document SHA-256 values and rejects or marks changed documents stale rather than evaluating replacement content under an old snapshot.

Dry extraction uses the draft projection as a constraint but does not call embedding persistence or graph write stages. Per-document outputs are retained only as validated evaluation summaries and evidence coordinates, not full document text or extracted graph payloads.

Alternative considered: randomly split discovery sources inside one run. Rejected because explicit held-out ownership is easier to explain, reproduce, and audit, and it does not reduce the evidence used to construct the draft unexpectedly.

### Define formulas for contractual metrics

Contractual metrics are deterministic calculations over validated dry-extraction observations:

- recognized entity rate = recognized entity observations / (recognized + unknown entity observations);
- dropped relationship rate = schema-rejected relationship observations / all relationship observations;
- key availability rate = recognized node observations with every configured key value / recognized node observations requiring keys;
- property type conflicts and missing required properties = validated observation counts;
- low-support and guided-without-evidence counts = current aggregate facts.

Zero denominators produce not-applicable values. The model may assist dry extraction, so the observations are model-dependent, but the calculation and reporting contract is deterministic.

Intended-question coverage and schema-noise assessments are explicitly advisory. They record profile and prompt revisions and cite schema coordinates, never document previews.

Alternative considered: advertise entity recall. Rejected because no annotated ground truth exists; “recognized rate” accurately describes the measured model observations.

### Reuse the durable-run pattern for evaluation

Evaluation runs and per-document outcomes use the same bounded executor, claim/lease, incremental persistence, restart recovery, status, and retry approach as draft analysis. Reuse keys additionally include the evaluated projection content hash and evaluation contract revision.

### Publish through `SchemaRegistryService`

Publication first builds and validates the exact effective projection, calculates its content hash, and verifies a revision-specific readiness token. A transactionally guarded publication record claims the draft and target identity, then calls a registry application boundary that performs normal schema validation, creates an inactive generated schema, and associates it with the knowledge base.

The draft becomes read-only and stores the published schema ID plus publication hash. The registered schema remains governed by the current contract: its name/version identity is immutable, its content can be edited or it can be deleted while inactive, and it cannot be edited or deleted while active. Draft retrieval compares the live schema hash with the publication hash to show post-publication drift without preventing it.

Alternative considered: add a locked/published schema status. Rejected because the user explicitly wants published inactive schemas to remain editable and existing mutation behavior already supplies the desired lifecycle.

### Keep publish, activate, and reprocess as separate commands

`POST .../publish` creates only the inactive schema. Existing activation remains the sole way to make it active. A new reprocessing-plan operation is available only after the published schema is active. This makes every externally consequential transition explicit and prevents a publication retry from triggering repeated processing.

Alternative considered: one publish-and-migrate endpoint. Rejected because partial activation or reprocessing failure would make publication idempotency and user control much harder.

### Orchestrate existing document processing per plan item

A plan snapshots selected document IDs/SHA-256 values, target schema ID/hash, and profile revision. A bounded worker verifies the document snapshot and active target schema immediately before invoking the existing processing service with overwrite enabled. Each item persists independently and uses current processing-run behavior.

If the active schema changes, queued items become blocked; already running calls complete under the client/schema context resolved for that invocation, and no new items start. Retry creates a new linked plan and skips matching successes.

Alternative considered: bulk-delete and rebuild graph artifacts in one transaction. Rejected because it would bypass established cleanup, previous-success preservation, processing options, profile routing, and run auditability.

## Risks / Trade-offs

- [Dry-extraction metrics can look objective despite model variability] → Name metrics precisely, publish formulas, record model/prompt revisions, and label advisory judgments separately.
- [Publication spans draft and registry state] → Use a unique publication claim, Neo4j transaction boundaries, content-hash preconditions, and idempotent lookup by draft ID.
- [Editing the published inactive schema diverges from reviewed evidence] → Preserve the publication hash and show drift in draft/publication responses; do not misrepresent the draft as evidence for later edits.
- [Large reprocessing plans can saturate AI providers and Neo4j] → Apply bounded plan and per-document concurrency, queue limits, and visible progress.
- [Active schema changes mid-plan] → Recheck before every queued item and block remaining work immediately.
- [Retry could process a changed document unintentionally] → Persist document SHA-256 per item and require explicit resnapshot behavior when retrying stale items.

## Migration Plan

1. Add evaluation run persistence and dry-extraction adapters, initially with advisory assessment disabled by default.
2. Add deterministic metric calculators and held-out eligibility enforcement.
3. Add readiness calculation and publication claim/link persistence around `SchemaRegistryService`.
4. Add reprocessing plan/item persistence and the bounded orchestrator around existing `DocumentProcessingService`.
5. Add startup recovery for interrupted evaluation and reprocessing work.
6. Deploy additively; existing schema generation, activation, schema mutation, and direct document processing APIs remain unchanged.
7. Roll back by disabling the new workers and endpoints. Published schemas remain valid normal registry records; draft publication links and run history can remain inert for audit.

## Open Questions

None.
