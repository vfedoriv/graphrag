## Context

`SchemaDraftReviewService.diff()` currently builds the effective current projection, selects comparison content through `comparisonSchema()`, and returns only the current aggregate ID and flattened changes. A configured base schema is read live; otherwise the service loads every aggregate by descending revision and treats the second entry as the previous aggregate. Retained non-current analyses participate in that ordering, while the response gives clients no way to identify which content was selected.

The main `schema-draft-review` specification already requires deterministic comparison against the base schema or prior effective aggregate. The adjacent frontend uses strict response validation, so an additive JSON response change is operationally breaking unless the compatibility frontend is deployed first.

## Goals / Non-Goals

**Goals:**

- Return enough typed metadata for clients to describe the exact comparison honestly.
- Make the no-base predecessor the aggregate that was current immediately before the present aggregate, not an arbitrary persisted revision.
- Preserve immutable comparison content so base-schema edits, retained non-current analyses, and later decisions cannot silently change the baseline.
- Keep existing diff item ordering, values, operations, and compatibility classification behavior.
- Support aggregates created before baseline snapshots exist.

**Non-Goals:**

- Attributing each change to a review decision or analysis source.
- Changing compatibility severity rules.
- Adding selectable comparison targets or a general historical diff API.
- Returning the complete baseline schema to clients.

## Decisions

### Return a structured baseline descriptor

Add `DiffBaselineType` values `BASE_SCHEMA`, `PREVIOUS_AGGREGATE`, and `EMPTY`, and return a nested descriptor with `type`, nullable `id`, and `contentHash`. Add `draftRevision` to `DiffResponse` beside the existing current `aggregateRevisionId`.

The nested shape keeps baseline metadata cohesive and leaves room for future audit metadata without adding unrelated top-level fields. `id` is the schema definition ID for `BASE_SCHEMA`, the predecessor aggregate ID for `PREVIOUS_AGGREGATE`, and null for `EMPTY`. `contentHash` identifies the exact canonical content that produced the `before` side.

Alternatives considered:

- Top-level `baselineType` and `baselineId`: smaller initially, but less cohesive and unable to identify mutable content exactly.
- Deriving the baseline in clients: rejected because clients cannot reliably distinguish configured bases, non-current results, or promotion history.

### Snapshot baseline lineage and content on aggregate creation

Extend aggregate persistence with nullable baseline type, ID, canonical schema JSON, and content hash. When an analysis is eligible to become current, capture the baseline before replacing `draft.currentAggregateId`:

- `BASE_SCHEMA`: snapshot the associated schema content and ID.
- `PREVIOUS_AGGREGATE`: snapshot the effective projection of the aggregate currently referenced by the draft and record that aggregate ID.
- `EMPTY`: snapshot the canonical empty schema and a null ID when no current aggregate exists.

The snapshot is owned by the new aggregate. A retained non-current aggregate cannot become the predecessor merely because it has a higher persistence revision. Capturing the prior effective projection also prevents later decisions from rewriting the meaning of an already established baseline.

Alternatives considered:

- Continue selecting `revisions[1]`: rejected because persistence order is not promotion lineage.
- Store only a predecessor ID and recompute its projection: rejected because decisions and mutable base-schema content can change after promotion.

### Use a deterministic compatibility path for legacy aggregates

Existing aggregates have no stored baseline snapshot. For such a current aggregate, resolve a legacy baseline deterministically from its configured base or the best available prior promoted analysis lineage, compute its canonical hash, and return the same descriptor shape without mutating data from the read endpoint. All newly promoted aggregates use stored snapshots. Regression tests will distinguish the legacy compatibility path from the authoritative snapshot path.

This avoids a risky bulk migration while ensuring existing drafts remain readable immediately after deployment. The legacy path is explicitly transitional and must never override a stored snapshot.

### Coordinate the strict-client rollout

The backend OpenAPI contract will document the nested descriptor and enum values. The frontend change will first accept an optional `baseline` and `draftRevision`, render an honest unavailable fallback for old backends, and only then may this backend response expansion be deployed. Once mixed versions are no longer supported, the frontend can tighten the fields to required.

## Risks / Trade-offs

- [Legacy data cannot always prove historical promotion lineage] → Use the best persisted promotion evidence only for legacy aggregates and make all future lineage authoritative through snapshots.
- [Snapshot JSON increases aggregate storage] → Store one canonical comparison schema per aggregate; schema drafts are bounded review artifacts and reproducibility outweighs the duplication.
- [Base schema changes after draft creation] → New aggregate snapshots freeze the exact content used for that diff and expose its hash.
- [Old strict clients reject the expanded response] → Deploy the rollout-compatible frontend before the backend contract expansion.
- [Current projection changes after a decision] → Return `draftRevision` so clients can bind the diff to the review state while keeping the baseline snapshot fixed.

## Migration Plan

1. Deploy frontend parsing that accepts the optional nested baseline descriptor and draft revision.
2. Add nullable aggregate baseline fields and snapshot creation without requiring a bulk Neo4j migration.
3. Expand `DiffResponse` and OpenAPI documentation, using the legacy compatibility path only when stored metadata is absent.
4. Verify base-schema, previous-current, empty, retained-non-current, mutable-base, and legacy aggregate cases.
5. Roll back safely by deploying the prior backend; added Neo4j properties remain unused and do not prevent old code from reading aggregates.

## Open Questions

- Whether a later audit-focused change should expose decision/source provenance per diff item; this change deliberately limits itself to baseline truthfulness and determinism.
