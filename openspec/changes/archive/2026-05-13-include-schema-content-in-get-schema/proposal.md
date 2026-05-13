## Why

Clients retrieving a schema by ID currently do not receive the raw schema content in the response, which forces extra calls or out-of-band lookup to inspect the actual schema definition. Returning the schema content in the same response makes schema retrieval complete and reduces client-side complexity.

## What Changes

- Extend `GET /api/v1/schemas/{schemaId}` response payload to include the persisted schema content.
- Ensure `SchemaController#getSchema()` returns the schema content field consistently for existing schema records.
- Preserve existing response fields and endpoint behavior while adding the new field.

## Capabilities

### New Capabilities
- `schema-retrieval-with-content`: Return schema metadata and schema content in single-schema retrieval responses.

### Modified Capabilities
- None.

## Impact

- Affected API: `GET /api/v1/schemas/{schemaId}` response contract.
- Affected code: schema response DTO/mapping, `SchemaController`, and related service mapping path.
- Tests: controller/service integration tests for schema retrieval response shape.
