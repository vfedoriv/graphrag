## Why

Graph extraction fails when the model returns valid but unexpected fields (for example `id` on nodes), causing document processing to abort with a Jackson `UnrecognizedPropertyException`. This makes ingestion brittle and sensitive to harmless LLM output variance.

## What Changes

- Make graph extraction JSON deserialization resilient to unknown fields in model output.
- Preserve strict validation for schema-constrained labels, relationship types, and allowed properties in post-deserialization validation.
- Add structured warnings when unknown fields are detected so operators can track prompt/output drift without breaking ingestion.
- Extend tests to cover unknown-field responses and confirm extraction continues safely.

## Capabilities

### New Capabilities
- `graph-extraction-response-tolerance`: Graph extraction accepts and safely ignores unknown model JSON fields while recording warnings.

### Modified Capabilities
- None.

## Impact

- Affected code: graph extraction client and extraction validation flow.
- API behavior: document processing no longer fails solely due to unknown JSON keys in model response.
- Observability: additional warning logs/metrics for ignored fields.
- Testing: new unit/integration coverage for tolerant parsing behavior.
