## Context

The legacy endpoint is synchronous and vector-led, while advanced search is durable, multi-retriever, cited, and cancellation-aware. Several valuable components currently use `HybridSearch*` DTOs, so they must be migrated before those public types can be deleted.

This is proposal 7 of 7 and requires all six preceding proposals. Keeping retirement separate allows advanced search to run alongside the legacy endpoint during evaluation and lexical-index warm-up.

## Goals / Non-Goals

**Goals:**

- Remove one obsolete public contract and its implementation after verified replacement.
- Preserve reusable dense retrieval, context expansion, and citation logic under advanced-search-owned models.
- Migrate only settings with equivalent semantics and document every retired key.

**Non-Goals:**

- Remove or merge generic `/queries/ask`.
- Preserve backward compatibility for `/queries/hybrid-search`.
- Add new retrieval or synthesis behavior.

## Decisions

### Gate removal on measured readiness

Before removal, versioned fixtures must demonstrate 100% KB isolation and citation referential integrity, at least ten percentage points Recall@10 improvement for mixed questions, no more than two points semantic-only regression, deadline compliance, and partial results on optional-branch failure.

### Migrate internals before deleting DTOs

Parent expansion and evidence assembly move to neutral advanced-search candidate/citation contracts in earlier proposals. Only then are `HybridSearchService`, controller mapping, and public `HybridSearch*` types deleted.

### Migrate settings by semantic equivalence

Evidence-text and candidate maximum overrides may migrate when their meanings match. Candidate multiplier and default graph depth are retired because the new pipeline has no equivalent semantics. Migration is idempotent and never overwrites an explicitly configured advanced key.

## Risks / Trade-offs

- [Clients are still using the endpoint] → Announce the breaking route, publish advanced run examples, and stage removal only after usage/readiness review.
- [Settings migration changes behavior] → Map only exact semantic equivalents and report ignored obsolete keys.
- [Hidden hybrid DTO dependencies remain] → Use compile-time removal and repository-wide tests before merge.

## Migration Plan

1. Run the versioned retrieval/answer evaluation suite and record acceptance results.
2. Warm lexical indexes for active knowledge bases and verify advanced run operations.
3. Idempotently migrate compatible persisted settings.
4. Remove endpoint, service, DTOs, obsolete settings, `MENTIONS` traversal, and tests.
5. Update synchronized documentation and OpenAPI examples.
6. Roll back by reverting this final change only; preceding advanced-search capabilities remain intact.

## Open Questions

None.
