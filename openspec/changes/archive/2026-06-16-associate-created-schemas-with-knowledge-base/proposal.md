## Why

Schemas created from the frontend can be persisted globally but remain invisible in the selected workspace because the knowledge-base schema list only returns schemas connected by `USES_SCHEMA`. The UI expectation is that a schema created while working in a knowledge base is immediately associated with that knowledge base without requiring activation.

## What Changes

- Allow schema creation requests to optionally include a knowledge base identifier.
- When a knowledge base identifier is provided, persist the schema and create the `KnowledgeBase` -> `USES_SCHEMA` -> `SchemaDefinition` association in the same backend operation.
- Keep newly associated schemas inactive unless the client explicitly calls the existing activation endpoint.
- Add a dedicated attach operation so an existing global schema can be associated with a knowledge base without activating it.
- Preserve the existing global schema creation behavior for clients that do not provide a knowledge base identifier.

## Capabilities

### New Capabilities

### Modified Capabilities

- `schema-list-by-knowledge-base`: schemas created or attached for a knowledge base must appear in that knowledge base's schema list without requiring activation.

## Impact

- Affected APIs: `POST /api/v1/schemas`, `GET /api/v1/knowledge-bases/{knowledgeBaseId}/schemas`, and a new KB-scoped attach endpoint.
- Affected backend areas: schema request DTOs, `SchemaController`, `KnowledgeBaseController`, `SchemaRegistryService`, and Neo4j repository/query logic for `USES_SCHEMA`.
- Tests should cover create-with-knowledge-base, attach-existing-schema, non-activation behavior, unknown knowledge base/schema errors, idempotent association, and unchanged global creation behavior.
