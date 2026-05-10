## Why

`LLMGraphTransformer` currently returns graph nodes and edges with empty `properties`, which causes downstream schema generation to lose inferred metadata. We need a controlled extension to restore correct property extraction and make schema output reliable.

## What Changes

- Add `LLMGraphTransformerExt` that extends `LLMGraphTransformer` and overrides `transform()` to correctly populate `GraphNode.properties` and `GraphEdge.properties`.
- Route schema generation to use the new transformer extension instead of the base implementation where properties are dropped.
- Add focused tests for `LLMGraphTransformerExt` property extraction behavior based on known Python implementation behavior examples in `LLMGraphTransformer_python_example`.
- Keep existing API contracts unchanged; this is an internal extraction quality fix.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `schema-generation-with-examples`: ensure inferred graph output used for schema generation includes non-empty node/edge properties when present in model output.

## Impact

- Affected code:
  - Graph transformation wrapper/extension class in schema generation flow
  - `LangChain4jSchemaGenerationService` integration point that instantiates transformer
  - New unit tests for transformer property parsing and mapping behavior
- API impact:
  - No endpoint contract changes
  - Improved generated YAML quality due to preserved inferred properties
- Dependencies:
  - No new dependency expected; reuse current LangChain4j + Spring AI stack
