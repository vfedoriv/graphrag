## Why

Schema authors can create, validate, list, retrieve, and activate schema definitions, but they cannot correct or remove schema records through the API. This leaves rejected drafts and mistaken schema submissions stuck in the registry unless operators edit Neo4j directly.

## What Changes

- Add an API operation to update an existing schema definition record by id while preserving the immutable `name + version` identity.
- Add an API operation to delete an existing schema definition record by id.
- Validate updated schema content using the existing JSON schema parser and validator before persistence.
- Protect active schemas from unsafe update/delete while allowing inactive associated schemas to be changed or removed.
- Detach inactive schema associations when deleting a schema.
- Preserve existing RFC 7807 error behavior for invalid, missing, or conflicting requests.

## Capabilities

### New Capabilities
- `schema-mutation`: Defines update and delete behavior for persisted schema definitions.

### Modified Capabilities
- `schema-json-format-enforcement`: Schema update becomes another lifecycle operation that accepts JSON schema definitions only.
- `schema-definition-validation`: Schema update must enforce the same schema definition validation rules as schema creation.
- `single-active-schema-per-knowledge-base`: Schema deletion must preserve the single-active-schema invariant and reject deletion of schemas currently active for a knowledge base.

## Impact

- API: add `PUT /api/v1/schemas/{schemaId}` and `DELETE /api/v1/schemas/{schemaId}` under the existing `/api/v1` schema controller.
- DTOs: add an update request contract or reuse a compatible schema content/source-type request shape if appropriate.
- Services/repositories: extend `SchemaRegistryService` and `SchemaDefinitionRepository` with update/delete operations, active-schema checks, and inactive association detach support.
- Tests: add controller/service/integration coverage for successful update/delete, validation failures, not-found behavior, immutable identity conflicts, active-schema guards, and inactive association detachment.
