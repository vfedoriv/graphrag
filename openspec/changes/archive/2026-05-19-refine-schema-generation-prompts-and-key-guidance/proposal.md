## Why

Schema generation prompts currently show property examples as array-valued JSON fields (for example `"head_properties": {"name": ["..."]}`), which can mislead the model into producing array semantics where simple key/value pairs are expected. Additionally, key guidance language is too strict/ambiguous and does not effectively steer the model toward selecting realistic canonical identity properties.

## What Changes

- Update `LLMGraphTransformerExt.USER_TEMPLATE` and related instructions so `head_properties`, `relation_properties`, and `tail_properties` are explicitly key/value object pairs with string values (not array examples).
- Refine schema-generation guidance injected from `LangChain4jSchemaGenerationService` to provide clearer canonical-key heuristics (for example, manufacturer+product name, model code, person fullName+birth date), and allow node `key` to contain a list of property names for composite identity.
- Align post-generation logic with revised prompting so warnings remain advisory and behavior remains deterministic when generated keys do not map to declared properties.
- Remove backward-compatibility handling for array-valued property outputs in schema generation path; generated properties must be key/value string pairs.
- Revalidate tests and adjust prompt assertions/fixtures that currently depend on array-shaped property examples.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `schema-generation-with-examples`: clarify prompt contract and response advisory behavior for node key selection and property object shape during schema generation.

## Impact

- Affected code:
  - `src/main/java/io/github/vfedoriv/graphrag/graph/LLMGraphTransformerExt.java`
  - `src/main/java/io/github/vfedoriv/graphrag/service/LangChain4jSchemaGenerationService.java`
  - schema generation warning/response mapping and related tests
- API impact: no breaking endpoint changes; warning fields remain additive/advisory.
- Dependencies/systems: no new runtime dependencies.
