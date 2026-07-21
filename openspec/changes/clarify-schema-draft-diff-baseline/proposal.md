## Why

Schema-draft diffs expose compatibility changes without identifying the schema used as the comparison baseline, so intentional review outcomes can look like unexplained data loss. The current no-base fallback also infers the previous aggregate by revision position, which can select a retained non-current analysis and does not preserve the exact compared content for deterministic audit.

## What Changes

- **BREAKING** Expand `DiffResponse` with the draft revision and a typed baseline descriptor containing `type`, nullable `id`, and `contentHash`; strict response validators must be updated before the backend expansion is deployed.
- Resolve no-base diff lineage from the aggregate that was current immediately before the present aggregate, excluding retained non-current analysis results.
- Snapshot the exact base-schema, previous-current-aggregate, or empty comparison content used by a promoted aggregate so repeated diff requests remain reproducible.
- Keep existing diff coordinates, operations, compatibility classifications, ordering, and before/after values unchanged.
- Document `BASE_SCHEMA`, `PREVIOUS_AGGREGATE`, and `EMPTY` baseline semantics and add contract and lifecycle regression coverage.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-review`: Make compatibility diffs identify and deterministically preserve their exact comparison baseline, including promotion-aware previous-aggregate lineage.

## Impact

- Affected backend code: schema-draft aggregate persistence and promotion, diff baseline resolution, `DiffResponse` DTO/OpenAPI output, repositories, and schema-draft lifecycle tests.
- Persistence: promoted aggregates require immutable comparison-baseline lineage and content sufficient to reproduce the diff.
- API consumers: strict clients must recognize the expanded response; the adjacent frontend change `clarify-schema-draft-diff-baseline` provides rollout-compatible parsing and presentation.
- No changes to schema compatibility classification rules or review-decision semantics.
