## Context

The draft API currently accepts `Object` guidance, omits the guidance value from `DraftResponse`, returns candidates as an ad hoc `Map`, and converts already typed evaluation-domain records back to `Object`. Analysis, evaluation, and reprocessing status responses also expose a page slice as a list plus a separate total-count field. These choices preserve runtime JSON but prevent useful OpenAPI schemas and force clients to infer meanings.

Two similarly named review states must also remain distinct: `DiscoveryContracts.ReviewState` is an analyzer recommendation, while `SchemaDraftReviewState` is the latest persisted user decision. The current candidate response exposes only the former.

Persisted guidance and evaluation JSON are durable audit data. Existing guidance may be a direct structured object, an object containing only `additionalInstructions`, or a wrapper containing `guidance` and `additionalInstructions`. Existing evaluation runs use contract revision `schema-draft-evaluation-v1`.

## Goals / Non-Goals

**Goals:**

- Give generated API clients complete, stable types for guidance, candidates, metrics, advisory results, and outcome pages.
- Let clients round-trip existing guidance without clearing or reshaping it accidentally.
- Preserve historical guidance and evaluation records without an eager Neo4j migration.
- Make not-applicable metrics and advisory failures explicit rather than encoding them as ambiguous nulls or strings.

**Non-Goals:**

- Adding run-history discovery or current-workflow summary endpoints.
- Changing metric formulas, candidate aggregation, review decision semantics, or reprocessing behavior.
- Exposing source text, guidance in logs, model responses, or extracted graph payloads.
- Providing compatibility aliases indefinitely for the old paged response shapes.

## Decisions

### Use one typed page envelope for bounded collections

Introduce `PageResponse<T>(int page, int size, long totalElements, List<T> content)` as the public envelope. Candidate retrieval returns this envelope directly. The existing `sourceOutcomes`, `outcomes`, and `items` properties in detailed status responses become page envelopes and the parallel `sourceOutcomeCount`, `outcomeCount`, and `itemCount` properties are removed.

Page numbers remain zero-based, requested sizes remain bounded to 1–100, and each endpoint retains its deterministic item ordering. This changes JSON shapes but removes four subtly different pagination conventions.

Alternative considered: keep list and count fields and add `page` and `size` beside them. Rejected because it would still require a special frontend adapter for status endpoints and would not establish a reusable page contract for the navigation change.

### Represent draft guidance as a canonical envelope

Define a draft-specific guidance DTO containing `additionalInstructions` and the existing structured `SchemaDiscoveryRequest.DiscoveryGuidance`. Create and guidance-update requests use this DTO; draft detail and list responses return it together with `guidanceRevision` and `guidanceFingerprint`. Null structured guidance normalizes to `DiscoveryGuidance.empty()` and list fields normalize to empty lists.

The persistence reader accepts all legacy shapes currently understood by `SchemaDraftAnalysisService` and maps them into the canonical envelope. New writes store only the canonical shape. Unknown fields and invalid structured values are rejected at the HTTP boundary before a revision changes.

Alternative considered: expose a separate guidance endpoint. Rejected because guidance is small draft state needed whenever an editor opens, and including it in `DraftResponse` keeps optimistic revision and fingerprint context together. A dedicated endpoint can be added later only if list payload size becomes material.

### Wrap discovery candidates with persistent review state

Define `CandidateResponse` with the explicit discovery candidate fields, rename the analyzer's recommendation to `recommendationState`, and add nullable `effectiveReviewState` plus nullable `latestDecisionId`. The service resolves the latest append-only decision by candidate identity without replacing or hiding the evidence-backed candidate. Rejected candidates therefore remain visible with `effectiveReviewState=REJECTED`.

Alternative considered: serialize `DiscoveryContracts.Candidate` directly and add another `reviewState` field. Rejected because two fields named review state with different enum domains invite incorrect UI behavior.

### Version and type evaluation result contracts

Expose typed rate and count metric records with a stable metric identifier, numerator/denominator where applicable, nullable value, explicit applicability enum, and evidence coordinates. Aggregate and per-document metrics use an explicit `EvaluationMetricsResponse`. Advisory results use enums for execution status and question coverage, lists of reasons and schema coordinates, and a reproducibility object containing profile, prompt, and contract revisions.

New runs write `schema-draft-evaluation-v2`; the revision remains part of reuse keys, so v1 outcomes are not reused as v2 results. Reads adapt persisted v1 JSON into the v2 response model using empty evidence/reason lists where v1 did not persist those fields while retaining the run's original contract revision.

Alternative considered: only change DTO return types to the existing internal `Metrics` and `AdvisoryAssessment` records. Rejected because their free-form status/assessment strings and missing per-result reasons would still leave important frontend behavior untyped.

### Keep public JSON contracts separate from persistence records

DTO mappers own legacy normalization and response construction; Neo4j node JSON fields remain internal audit storage. This prevents future public contract evolution from requiring direct exposure of persistence shapes and avoids returning `Map<String,Object>` or plain `Object` from controllers.

## Risks / Trade-offs

- [Existing draft clients depend on old outcome-list fields] → Mark the page change as breaking, document the exact replacement, and update backend contract tests before frontend integration.
- [Legacy guidance contains an unsupported arbitrary shape] → Fail that draft read with a privacy-safe persisted-state error rather than silently discard fields; cover every shape produced by existing tests and services.
- [Historical v1 evaluation records lack new evidence or reason fields] → Return deterministic empty lists and the original contract revision so the client can distinguish legacy completeness.
- [Adding guidance to every draft list item increases payload size] → Guidance is bounded by existing validation limits; measure list payloads and introduce summary/detail DTO separation later if needed.
- [Generic Java records can produce weak OpenAPI component names] → Add explicit schema names/annotations or concrete wrapper records where Springdoc cannot preserve generic specialization.

## Migration Plan

1. Add the page, guidance, candidate, metric, advisory, and reproducibility DTOs plus legacy JSON adapters.
2. Convert guidance request handling and responses while retaining legacy persisted reads.
3. Convert candidates and detailed outcome pagination, then update OpenAPI/controller contract tests.
4. Write v2 evaluation results and adapt v1 reads; do not rewrite historical nodes.
5. Coordinate the breaking page response release with the frontend and remove old list-plus-count fields in the same deployment.
6. Roll back by restoring the prior DTO projections; canonical guidance and v2 evaluation JSON remain parseable data and must not be deleted.

## Open Questions

None.
