## Context

Analysis, evaluation, and reprocessing are durable resources, but their controllers expose only create, identifier-specific status, and retry operations. Analysis and evaluation repositories already have draft-ordered history reads; reprocessing lacks the equivalent query. A client that loses a returned identifier cannot discover running or historical work.

The current analysis node persists `currentResult`, but earlier successful runs are not necessarily cleared when a later aggregate becomes current. Evaluation has enough snapshot metadata to calculate currentness but no public flag. Analysis retries do not persist a parent run identifier, while evaluation and reprocessing already do.

Held-out selection currently rejects every active document source. The existing specification is narrower: only a document source that contributed active evidence to the evaluated aggregate is ineligible. The current aggregate identifies its producing run, whose successful source results provide the authoritative evidence membership.

This change assumes the page contract from `harden-schema-draft-api-contracts` and does not alter detailed outcome payloads.

## Goals / Non-Goals

**Goals:**

- Recover workflow state after reload or from a second client without retained run identifiers.
- Define current, latest, retryable, and retry-lineage semantics explicitly.
- Let the frontend query the backend's exact held-out eligibility decision before starting evaluation.
- Keep list responses bounded and lightweight enough for overview and polling recovery.
- Avoid N+1 repository access when draft lists include workflow summaries.

**Non-Goals:**

- Streaming progress, push notifications, cancellation, deletion, or retention policies for runs and plans.
- Returning full outcome pages in history summaries.
- Automatically retrying, publishing, activating, or reprocessing work.
- Changing document ownership, draft mutation, or evaluation metric contracts.

## Decisions

### Add paged summary endpoints rather than full status history

Add paged `GET .../{draftId}/analysis-runs` and `GET .../{draftId}/evaluation-runs` endpoints and a paged `GET .../reprocessing-plans` endpoint with optional `draftId` filtering. Each item is a summary containing identity, status, aggregate counts, timestamps, retryability, lineage, and current/latest flags. Detailed status endpoints remain the only responses that include per-source or per-document outcome pages.

All history lists order by `createdAt` descending with identifier as a deterministic tie-breaker and bound page size to 1–100.

Alternative considered: add only `/current` endpoints. Rejected because they would recover active polling but not historical audit links, retry parents, or completed work after a second session starts.

### Derive currentness from authoritative draft state

An analysis summary is current when its in-flight snapshot still matches the current draft inputs, or when its terminal aggregate identifier equals the draft's `currentAggregateId`. Responses do not trust old persisted `currentResult=true` values alone.

An evaluation summary is current when its draft revision, aggregate revision, and projection content hash still match the draft's effective reviewed projection. A latest run can therefore be non-current after a decision or guidance change.

A reprocessing summary uses `latest` for ordering semantics and separately reports whether its target schema is still the active schema with the snapshotted content hash. It is not described as current draft evidence.

Alternative considered: overwrite boolean flags on every older run whenever a draft changes. Rejected because derived currentness is correct for historical data and avoids multi-row mutation on every draft edit.

### Persist exact analysis retry lineage

Add nullable `retryOfRunId` to analysis runs. The retry operation passes the selected parent into new-run creation; normal starts and historical runs have null lineage. If start idempotency returns an already-running matching run, it does not rewrite that run's lineage.

Retryability in summaries reflects whether the corresponding retry command would be accepted in the current resource state, not merely whether the last failure category was provider-retryable.

Alternative considered: infer retry lineage from matching snapshot fingerprints and timestamps. Rejected because independent manual starts can share a snapshot and inference would corrupt the audit trail.

### Share one held-out eligibility resolver with evaluation start

Create a service-level eligibility resolver used by both the new endpoint and `start` validation. It starts from owned knowledge-base documents and marks `ACTIVE_DISCOVERY_EVIDENCE` only when the document-backed active source revision has a successful result in the run that produced the current aggregate. Eligible items have a null reason. Results are paged and ordered consistently with document listing.

Alternative considered: let the frontend subtract active draft sources. Rejected because it cannot distinguish failed, removed, superseded, or non-contributing source revisions and would duplicate a publication-critical backend rule.

### Embed lightweight workflow references in draft responses

Draft responses include nullable current-analysis, latest-evaluation, and latest-reprocessing summaries containing only identifier, status, and the relevant current/latest flag. Draft detail may reuse the same summaries returned in draft lists. Repository methods fetch summaries for a set of draft identifiers in bounded batch queries so listing drafts does not run three queries per row.

Alternative considered: require every overview to call all history endpoints. Rejected because it multiplies requests and makes polling recovery expensive for draft lists.

### Preserve ownership boundaries in every query

Draft-owned history requires both knowledge-base and draft ownership before querying child resources. Reprocessing history is constrained by knowledge-base ID and, when supplied, verifies that the draft belongs to that knowledge base. Foreign identifiers produce the established ownership-safe not-found behavior rather than empty lists that disclose resource existence.

## Risks / Trade-offs

- [Summary currentness drifts from start/read validation] → Centralize snapshot/currentness helpers and cover both endpoint and command paths with the same fixtures.
- [Draft list summaries create expensive graph queries] → Use bounded batch projections keyed by draft IDs and measure query counts in integration tests.
- [Historical analysis rows have no retry parent] → Treat null as a valid root and add lineage only for new retries.
- [Eligibility queries race with a draft mutation] → Compute against a captured draft revision/current aggregate and return that revision so the frontend can pass it to evaluation start; normal optimistic checks reject stale starts.
- [Plan retryability requires active-schema state] → Define it from current target validity and unresolved outcome counts, and keep retry command validation authoritative.
- [Two active changes touch draft DTOs] → Apply the contract-hardening change first and rebase navigation DTO work on its typed page/guidance model.

## Migration Plan

1. Implement shared currentness and held-out eligibility resolvers with repository-level tests.
2. Add analysis retry lineage and history summary repository projections; leave historical lineage null.
3. Add evaluation and reprocessing history projections and endpoints using the standard page envelope.
4. Add workflow summaries to draft detail and batched draft listing.
5. Switch evaluation start validation to the shared eligibility resolver and add race/ownership tests.
6. Deploy additively after `harden-schema-draft-api-contracts`; no data rewrite is required.
7. Roll back by removing the additive endpoints and summary fields. The nullable analysis lineage property may remain inert.

## Open Questions

None.
