## Why

`LangChain4jSchemaGenerationService.inferSchema` currently drops graph metadata from `LLMGraphTransformer` output, so generated YAML often has empty node descriptions, empty node properties, and missing edge descriptions. This makes generated schemas low-quality and forces manual reconstruction of critical structure.

## What Changes

- Update schema inference mapping to preserve node descriptions.
- Update schema inference mapping to preserve node properties with names and inferred data types.
- Update schema inference mapping to preserve edge descriptions.
- Add/adjust tests to validate that generated schema includes non-empty node descriptions/properties and edge descriptions when returned by transformer output.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `schema-generation-with-examples`: Strengthen schema-generation behavior so inferred graph metadata from model output is retained in the generated YAML, including node descriptions, node properties, and edge descriptions.

## Impact

- Affected code:
  - `LangChain4jSchemaGenerationService` schema inference mapping logic
  - DTO/domain mapping classes used to build `GraphNode`, `GraphEdge`, and node `Property` sections in YAML
  - Unit/integration tests around schema generation output
- API impact:
  - No endpoint contract changes
  - Response quality/content changes (richer YAML metadata)
- Dependencies:
  - No new runtime dependencies expected
