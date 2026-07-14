## Why

Schema generation currently analyzes one text or file in one model call, so the result tends to model a single document rather than a representative domain. The system needs a review-only discovery path that analyzes multiple sources independently, applies explicit domain guidance, and merges evidence-backed candidates without allowing model output to silently resolve semantic conflicts.

## What Changes

- Add a knowledge-base-scoped, synchronous multi-source schema discovery operation with bounded source count and bounded concurrent model calls.
- Accept existing knowledge-base document references, multiple request-scoped files, and pasted text samples while preserving source boundaries.
- Add structured generation guidance alongside free-form additional instructions, including required, preferred, and excluded concepts; naming rules; identity and type guidance; and intended questions.
- Replace the opaque generated-example handoff in the new workflow with strongly typed node, relationship, and property candidates carrying evidence, support, confidence, and one or more evidence origins.
- Merge candidates deterministically in application code, preserve compatible additions, and surface key, type, synonym, relationship-name, and relationship-direction conflicts for review.
- Return a review-only schema projection, candidates, conflicts, warnings, and per-source outcomes without persisting a schema or mutating an active schema.
- Preserve all existing single-source schema and example generation endpoint contracts.

## Capabilities

### New Capabilities
- `multi-source-schema-discovery`: Multi-source input, structured generation guidance, evidence-backed candidate extraction, deterministic aggregation, conflict reporting, and review-only schema projection.

### Modified Capabilities

None.

## Impact

- Adds knowledge-base-scoped schema discovery API DTOs and controller operations under `/api/v1`.
- Extends schema-generation application collaborators with source preparation, typed model output, candidate normalization, aggregation, and conflict classification.
- Uses the knowledge base active AI profile and existing AI observation/privacy controls for every source model call.
- Reuses document parsing and binary reads for existing documents but does not persist request-scoped file or pasted-text sources.
- Adds deterministic unit tests, controller contract tests, AI profile/observation tests, and multi-source integration coverage.

