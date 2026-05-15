## Why

Graph extraction currently fails an entire chunk when the model returns a relationship triple not present in the active schema, even if the rest of the payload is valid. This reduces ingestion reliability for long documents and makes model variance operationally expensive.

## What Changes

- Make relationship validation tolerant by dropping schema-invalid relationships with warning logs instead of failing chunk extraction.
- Keep strict validation for allowed relationship triples and node labels on retained graph elements.
- Strengthen extraction prompt instructions with explicit allowed relationship triples from the active schema.
- Add explicit prompt rule: if no allowed triple applies, omit the relationship.
- Add tests proving invalid relationships are dropped while valid relationships and chunk processing continue.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `graph-extraction-response-tolerance`: Relationship-level schema violations are filtered with warnings, and prompt guidance is tightened to reduce invalid triples.

## Impact

- Affected code: extraction prompt builder, graph extraction validation flow, and extraction tests.
- Runtime behavior: document processing no longer fails solely because one extracted relationship uses a non-schema triple.
- Observability: warning logs for dropped relationships, including triple details and chunk context.
