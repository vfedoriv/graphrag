## Context

`SchemaController` currently exposes `/api/v1/schemas/generate` for text and `/api/v1/schemas/generate/from-file` for uploaded files. Both endpoints call `SchemaGenerationService.generateYaml(...)`, which creates an `LLMGraphTransformer`, transforms the source text into a `GraphDocument`, infers node and relationship definitions, and serializes a YAML `SchemaDocument`.

The current API does not provide the domain example needed to steer graph extraction, so the transformer has to infer both the domain ontology and the schema shape from the source content alone. The API also allows `save=true`, which stores generated YAML as a registry schema before a human has reviewed it.

## Goals / Non-Goals

**Goals:**

- Require a caller-provided graph example when generating schema YAML from text or file input.
- Remove direct persistence from schema generation endpoints; callers must review YAML and use the existing schema creation endpoint to save it.
- Add example-generation endpoints for text and file input, with optional `userPrompt` guidance that can describe the domain, preferred entity names, relationship names, and properties.
- Keep request validation, multipart parsing, OpenAPI documentation, and controller tests aligned with the new contract.

**Non-Goals:**

- Do not replace `LLMGraphTransformer` as the schema extraction mechanism.
- Do not create a separate review workflow or draft schema registry state.
- Do not automatically validate or activate generated schemas beyond the existing YAML generation and separate schema validation endpoints.

## Decisions

1. Schema generation accepts an explicit `example` value.

   `/schemas/generate` will add a required non-blank `example` field to the JSON request. `/schemas/generate/from-file` will add a required non-blank multipart form field named `example`. The example is treated as caller-supplied domain context for `LLMGraphTransformer`, not as source content to infer schema from.

   Alternative considered: generate the example internally as part of every schema generation request. That hides a meaningful prompt-review step from callers and makes schema generation slower and less predictable.

2. Schema generation becomes read-only.

   Remove the `save` request field/parameter and remove `SchemaRegistryService.createSchema(...)` calls from generation flows. `GenerateSchemaResponse` should contain generated YAML only, or at minimum no saved schema identifier. Persisting reviewed YAML remains the responsibility of `POST /api/v1/schemas`.

   Alternative considered: keep `save` but force it to `false`. That preserves a misleading API surface and leaves clients thinking generated schemas can still be directly registered.

3. Add dedicated example-generation endpoints.

   Introduce `POST /api/v1/schemas/generate/example` for JSON text input and `POST /api/v1/schemas/generate/example/from-file` for multipart file input. Both return a generated example string. The request includes source content plus optional `userPrompt`; file input reuses `DocumentParsingService` to extract text before calling the service.

   Alternative considered: overload the existing schema generation endpoints with a mode flag. Separate endpoints keep the two-step workflow explicit: generate/review example, then generate/review YAML.

4. Keep LLM-specific behavior behind `SchemaGenerationService`.

   Extend the service boundary to support `generateYaml(name, version, description, text, example)` and `generateExample(text, userPrompt)`. `LangChain4jSchemaGenerationService` owns model construction, transformer configuration, and prompt composition for examples.

   Alternative considered: add transformer-specific details to the controller. That would leak model orchestration into the API layer and make tests less focused.

## Risks / Trade-offs

- [Risk] Existing clients using `save` or omitting examples will break. → Mitigation: document the breaking API change in OpenAPI annotations and update tests around validation failures.
- [Risk] Generated examples may still be low quality if `userPrompt` is vague. → Mitigation: make the generated example visible to callers so they can edit it before schema generation.
- [Risk] LangChain4j example configuration API may differ by package/version. → Mitigation: verify the exact `LLMGraphTransformer` example hook against the project dependency during implementation and isolate it in `LangChain4jSchemaGenerationService`.
- [Risk] File example generation may process large uploads twice in a manual workflow. → Mitigation: reuse existing parsing behavior and keep this proposal focused on correctness; size controls can be handled separately if needed.
