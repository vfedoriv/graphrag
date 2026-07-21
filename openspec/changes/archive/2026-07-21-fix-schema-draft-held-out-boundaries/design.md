## Context

Schema draft analysis persists `sourceSha256` on every source result and promotes an aggregate only when the run snapshot still matches the draft's active source membership. Held-out eligibility currently walks from the current aggregate to successful results, then joins only active `DOCUMENT` sources and returns their document identifiers. This excludes a contributing document by identity but misses identical content contributed through draft-owned `FILE` or `TEXT` sources or uploaded again under another document identifier.

When `currentAggregateId` is null, the same query returns an empty exclusion set. The eligibility endpoint consequently labels every knowledge-base document eligible even though projection and evaluation start require a current aggregate. Normal knowledge-base document processing is already independent of draft services; the observed invalidation came from adding the same file through the draft-owned source endpoint before uploading it as a knowledge-base document.

## Goals / Non-Goals

**Goals:**

- Make held-out status depend on content that actually contributed to the current projection.
- Apply the same eligibility result to discovery and evaluation-start validation.
- Give clients a typed, actionable state when re-analysis is required.
- Preserve optimistic race protection and privacy-safe metadata handling.
- Retain the boundary between knowledge-base document processing and explicit draft-source mutation.

**Non-Goals:**

- Reserving documents permanently for evaluation or introducing a new document-role entity.
- Automatically removing an accidentally added draft source or rerunning analysis.
- Changing how draft files, pasted text, or knowledge-base documents are analyzed.
- Comparing parsed or normalized text beyond the existing binary SHA-256 identity.
- Reclassifying historical evaluation runs or rewriting persisted source results.

## Decisions

### Use successful source-result fingerprints from the current aggregate

Replace the document-ID exclusion query with a query that returns distinct, non-blank `sourceSha256` values from successful source results in the run that produced `draft.currentAggregateId`. Eligibility compares each owned knowledge-base document's SHA-256 against that set. The same resolver is used by both the eligibility listing and evaluation start.

The source result is the authoritative record because it captures the exact content revision that produced candidates. It already supports all three source types and avoids depending on a mutable current source node.

Alternative considered: compare only current `SchemaDraftSource` nodes. Rejected because a current source record alone does not prove that its revision succeeded or contributed to the promoted aggregate.

Alternative considered: continue using document identifiers and add special handling for `FILE`. Rejected because it still permits duplicate knowledge-base uploads and does not cover pasted text promoted into a document.

### Expose evaluation readiness separately from per-document eligibility

Extend the eligibility page with a typed readiness field and blocking reason. When `currentAggregateId` is null, report evaluation as not ready with `DRAFT_ANALYSIS_REQUIRED`; return document rows as non-selectable with the same reason rather than claiming they are held out. Evaluation start performs the same readiness check before document lookup, snapshot creation, or executor submission.

This gives the frontend enough information to explain that the discovery aggregate was invalidated and link to analysis recovery without inferring state from a nullable aggregate identifier.

Alternative considered: return an empty page or HTTP conflict from the listing endpoint. Rejected because the client still needs the document inventory and a typed recovery explanation, while the listing operation itself remains a valid read.

### Keep document processing orthogonal to draft state

Do not add draft identifiers or intent flags to upload or processing endpoints. A knowledge-base document affects a draft only through an explicit draft-source mutation. Add regression coverage proving upload and processing leave draft revision and `currentAggregateId` unchanged.

Alternative considered: make document processing aware of held-out reservations. Rejected because evaluation already performs dry extraction directly, and coupling the general document lifecycle to one draft would create ambiguous behavior when a knowledge base has multiple drafts.

### Use exact SHA-256 equality as the content boundary

Use the existing binary SHA-256 values persisted for sources, source results, and knowledge-base documents. This closes exact duplicate leakage without new persistence or content access and keeps eligibility queries metadata-only.

Alternative considered: normalized-text or semantic similarity detection. Rejected for this change because parser-dependent normalization would be expensive, could expose content handling to a read endpoint, and has unclear false-positive semantics.

## Risks / Trade-offs

- [The same logical text with different encoding or file packaging remains eligible] → Document exact-hash semantics and consider normalized-content identity separately if real workflows require it.
- [Adding enum values and readiness fields affects generated frontend clients] → Make fields additive, document all states, and update OpenAPI contract tests.
- [A stale aggregate could produce misleading fingerprints] → Resolve fingerprints only through `draft.currentAggregateId`; existing source-membership promotion rules remain authoritative.
- [Older source results might lack `sourceSha256`] → Ignore null or blank fingerprints and retain identifier-based exclusion only as a compatibility fallback for successful historical `DOCUMENT` results.
- [Eligibility could change between list and start] → Preserve draft revision and aggregate identity checks and resolve eligibility again during start.

## Migration Plan

1. Add the content-fingerprint repository projection with a compatibility path for historical document-backed results.
2. Extend evaluation eligibility DTOs and OpenAPI schemas with typed readiness and recovery reasons.
3. Centralize readiness and fingerprint decisions in `SchemaDraftEvaluationEligibilityService` and consume them from evaluation start.
4. Add integration coverage for `DOCUMENT`, `FILE`, `TEXT`, duplicate document IDs, missing aggregates, and upload/process non-mutation.
5. Deploy additively; no graph data rewrite is required.
6. Roll back by restoring identifier-only eligibility and ignoring the additive response fields; persisted data remains compatible.

## Open Questions

None.
