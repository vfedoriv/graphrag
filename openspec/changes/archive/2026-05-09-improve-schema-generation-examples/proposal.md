## Why

The schema generation endpoints currently call `LLMGraphTransformer` without the domain example it needs to extract representative graph entities and relationships, so generated YAML can be incomplete or misaligned with the source domain. Generated schemas are also saved directly when requested, which bypasses the human review expected before adding a schema version to the registry.

## What Changes

- **BREAKING** Remove the `save` option from `/schemas/generate` and `/schemas/generate/from-file`; generated YAML is returned only and must be reviewed before being submitted through the normal schema creation flow.
- **BREAKING** Require callers of `/schemas/generate` and `/schemas/generate/from-file` to provide a domain example containing representative entities and relationships for the input text or uploaded file.
- Add text and file endpoints that generate the required domain example from source content plus an optional `userPrompt` with domain guidance, preferred entity names, relationship hints, and property expectations.
- Validate that schema generation requests include non-blank text/file content and a non-blank example.
- Keep generated YAML validation-friendly while avoiding registry writes during schema generation.

## Capabilities

### New Capabilities
- `schema-generation-with-examples`: Graph schema YAML generation from text or uploaded files using caller-provided domain examples, without direct persistence.
- `schema-example-generation`: Domain example generation from text or uploaded files using optional user guidance.

### Modified Capabilities

## Impact

- Affected API endpoints: `/api/v1/schemas/generate`, `/api/v1/schemas/generate/from-file`, and new schema example generation endpoints.
- Affected code: `SchemaController`, schema generation DTOs/responses, `SchemaGenerationService`, `LangChain4jSchemaGenerationService`, OpenAPI annotations, and controller/service tests.
- Client impact: callers that use `save` or omit examples must update requests; schema persistence remains available through `POST /api/v1/schemas` after review.
