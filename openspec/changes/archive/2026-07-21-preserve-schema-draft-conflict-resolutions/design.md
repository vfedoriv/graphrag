## Context

Schema-draft conflicts are stored as aggregate-revision-owned nodes, but every analysis currently creates fresh unresolved nodes and the conflict-list operation reads all nodes by draft identifier. Candidate decisions have cross-analysis reconciliation, while conflict resolutions do not. Consequently, a review client sees resolved historical records beside equivalent unresolved current records, even though projection and publication readiness already filter conflicts to the current aggregate.

Historical conflict records must remain immutable and auditable. The active review surface, however, needs one unambiguous state for the current aggregate, and prior resolutions must only be reused when doing so cannot conceal changed analysis output.

## Goals / Non-Goals

**Goals:**

- Return only current-aggregate conflicts in the default review operation.
- Preserve explicit access to complete conflict history and aggregate lineage.
- Carry forward a valid prior resolution for semantically equivalent conflicts.
- Keep changed or invalid recurring conflicts unresolved.
- Make reconciliation and response ordering deterministic and testable.

**Non-Goals:**

- Deleting or rewriting historical conflict nodes.
- Changing candidate decision reconciliation.
- Changing which conflict types block publication.
- Automatically resolving conflicts that have never received an explicit user resolution.
- Inferring equivalence from labels, evidence text, or approximate similarity.

## Decisions

### Use an explicit semantic conflict key

Reconciliation will derive a semantic key from conflict type, coordinate, and a normalized alternative set. Alternative ordering will not affect the key: each alternative is canonically serialized, the serialized values are sorted, and the ordered result is fingerprinted or compared directly. Evidence is excluded because evidence provenance may grow or reorder while the decision alternatives remain identical.

Matching only type and coordinate was rejected because it could apply a stale choice after the analyzer changes the available alternatives. Including evidence was rejected because harmless provenance changes would unnecessarily discard valid decisions.

### Copy resolution state into the new aggregate record

Analysis will continue creating one conflict record per aggregate revision. Before saving a current conflict, reconciliation will search resolved historical conflicts for the same semantic key and use the most recently resolved matching record. The new record receives its own identifier, aggregate revision, timestamps, alternatives, and evidence, plus copied selected-alternative or custom-resolution state and a resolution timestamp reflecting the reconciliation event. Historical nodes remain unchanged.

Reusing the existing node was rejected because a conflict node belongs to one aggregate revision and reuse would corrupt lineage. Storing a draft-global mutable resolution record was deferred because it introduces a larger data-model migration than this issue requires.

### Validate carried resolutions

A selected alternative is reusable only when it is present in the normalized current alternative set. A custom resolution must pass the same structural validation used when resolving the current conflict. If validation fails or the historical payload is incomplete, the new conflict remains unresolved. Reconciliation never converts an unresolved historical conflict into an automatic resolution.

### Separate current listing from explicit history

The conflict-list API will accept a typed scope with `CURRENT` as the default and `ALL` as the explicit history value. `CURRENT` queries only the draft's current aggregate identifier; a draft without one returns an empty collection. `ALL` returns every conflict for the draft, ordered by aggregate revision descending and then by coordinate and conflict identifier for stable ties.

Each conflict response will include `aggregateRevisionId` and `current`. This permits clients to render history without deriving lineage and prevents an `ALL` response from presenting historical work as current. The review UI will use the default `CURRENT` scope; a history view can opt into `ALL`.

Changing the default is a deliberate contract correction. Keeping the draft-wide list as the default was rejected because it preserves the contradictory review state shown to users.

### Reconcile only an aggregate eligible to become current

Resolution carry-forward may be computed while persisting an aggregate, but current review semantics are determined by the draft's promoted aggregate identifier. If an analysis finishes against a stale draft snapshot and is not promoted, its conflict records remain historical and never enter the default list. This keeps promotion as the single source of currentness.

## Risks / Trade-offs

- [Canonicalization differs from analyzer equality] → Centralize semantic-key construction and cover reordered alternatives, structured alternatives, and Unicode/string values with tests.
- [An obsolete custom resolution passes shallow validation] → Reuse the existing conflict-resolution validator and retain final schema projection validation as a publication guard.
- [Concurrent analyses choose inconsistent historical matches] → Select the latest resolved match deterministically and rely on aggregate promotion to define which reconciled record is current.
- [Existing clients expected draft-wide results] → Document the default-scope change, add the explicit `ALL` scope, and update OpenAPI and frontend consumers together.
- [History queries grow over time] → Use aggregate-aware repository queries and retain deterministic pagination as a follow-up if observed history volume warrants it.

## Migration Plan

1. Add aggregate-scoped repository reads and semantic conflict-key/reconciliation logic without altering existing records.
2. Extend the conflict response and list request contract with lineage, currentness, and typed scope.
3. Change the review UI to consume current conflicts by default and expose history only through an explicit action if supported by the current screen.
4. Deploy without a data migration; existing conflict nodes already contain aggregate revision identifiers and can participate in history and matching.
5. Roll back by restoring the previous listing behavior and disabling reconciliation; newly copied resolution states remain valid auditable conflict records.

## Open Questions

- Whether conflict history should be paginated in this change or retain the existing collection shape until volume requires pagination.
