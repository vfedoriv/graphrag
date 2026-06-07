## 1. API Contract

- [x] 1.1 Add a schema update request DTO for replacement content and optional source type validation.
- [x] 1.2 Add `PUT /api/v1/schemas/{schemaId}` to `SchemaController` returning `SchemaDetailsResponse`.
- [x] 1.3 Add `DELETE /api/v1/schemas/{schemaId}` to `SchemaController` returning `204 No Content`.
- [x] 1.4 Add OpenAPI response annotations for success, invalid payload, not found, and conflict cases.

## 2. Registry Service

- [x] 2.1 Add `SchemaRegistryService.updateSchema(schemaId, content, sourceType)` with JSON parsing and schema validation.
- [x] 2.2 Preserve immutable schema identity by rejecting updates that change parsed `name` or `version`.
- [x] 2.3 Recalculate and persist content hash, content, source type, and JSON format for successful updates.
- [x] 2.4 Add `SchemaRegistryService.deleteSchema(schemaId)` with not-found handling.
- [x] 2.5 Reject update and delete operations for schemas referenced as an active schema by any knowledge base.
- [x] 2.6 Allow inactive associated schemas to be updated.
- [x] 2.7 Detach `USES_SCHEMA` relationships before deleting an inactive associated schema.

## 3. Repository Support

- [x] 3.1 Add repository queries to detach `USES_SCHEMA` associations for a schema id.
- [x] 3.2 Add repository queries to detect knowledge bases whose `activeSchemaId` matches a schema id.
- [x] 3.3 Ensure delete removes only the schema definition record and relationships pointing at that schema after active-schema guards pass.

## 4. Tests

- [x] 4.1 Add controller tests for update and delete routing, response status, response body, and service delegation.
- [x] 4.2 Add service unit tests for successful update, invalid content, not found, identity conflict, active-schema update conflict, and active-schema delete conflict.
- [x] 4.3 Add integration tests proving persisted update content/hash changes and deleted schemas are no longer retrievable.
- [x] 4.4 Add integration tests proving inactive associated schemas can be updated/deleted, delete detaches schema relationships, and active-schema references remain intact.
- [x] 4.5 Run `./mvnw test` and fix regressions.
