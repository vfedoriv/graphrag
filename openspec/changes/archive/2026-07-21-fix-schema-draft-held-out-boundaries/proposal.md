## Why

Held-out evaluation currently excludes only knowledge-base document identifiers that contributed through `DOCUMENT` draft sources. The same content can therefore contribute to schema discovery through a draft-owned `FILE` or `TEXT` source and later be accepted as held-out under a different document identifier, while a draft with no current aggregate misleadingly reports every knowledge-base document as eligible.

## What Changes

- Determine held-out eligibility from the content fingerprints that successfully contributed to the current aggregate, regardless of whether the discovery source was a knowledge-base document, draft-owned file, or pasted text.
- Reject a knowledge-base document as active discovery evidence when its SHA-256 matches contributed discovery content, even when its document identifier or source type differs.
- Represent the absence of a current aggregate as an explicit evaluation-not-ready state and prevent held-out selection or evaluation start until discovery analysis is current.
- Keep normal knowledge-base document upload and processing independent from draft membership: those operations do not advance a draft revision or invalidate its aggregate.
- Preserve optimistic revision and aggregate checks between eligibility discovery and evaluation start.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-evaluation`: Strengthen held-out eligibility to use contributed content identity across every draft source type and define behavior when the draft has no current aggregate.

## Impact

- Changes the schema-draft evaluation eligibility response and evaluation-start validation.
- Affects `SchemaDraftEvaluationEligibilityService`, its repository queries, evaluation DTO/OpenAPI contracts, and draft evaluation integration tests.
- May add a typed readiness state and ineligibility reason to the eligibility response; existing document processing endpoints and persistence remain unchanged.
- Existing evaluation history remains auditable and requires no data migration because source results already persist source identifiers/revisions and source snapshots persist SHA-256 fingerprints.
