## 1. API Contract

- [x] 1.1 Extend the schema creation request DTO to accept an optional `knowledgeBaseId` while preserving existing validation for schema content.
- [x] 1.2 Add a KB-scoped attach endpoint at `POST /api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach`.
- [x] 1.3 Ensure create and attach errors use the existing RFC 7807 `ProblemDetail` conventions for invalid payloads, unknown knowledge bases, unknown schemas, and conflicts.

## 2. Backend Behavior

- [x] 2.1 Add a transactional service path that creates a schema and, when `knowledgeBaseId` is present, validates the knowledge base and creates a `USES_SCHEMA` association.
- [x] 2.2 Add a service method to attach an existing schema to a knowledge base without changing `activeSchemaId` or schema status.
- [x] 2.3 Implement association persistence with idempotent `MERGE` semantics so repeated attach requests do not create duplicate relationships.
- [x] 2.4 Preserve existing global schema creation behavior when `knowledgeBaseId` is omitted.
- [x] 2.5 Preserve activation behavior so only the existing activation endpoint changes the active schema and deactivates siblings.

## 3. Tests

- [x] 3.1 Add service or repository coverage for create-with-knowledge-base creating exactly one `USES_SCHEMA` relationship while leaving the schema inactive.
- [x] 3.2 Add controller coverage showing `POST /api/v1/schemas` with `knowledgeBaseId` returns the created schema and the KB schema list includes it.
- [x] 3.3 Add controller coverage for `POST /api/v1/knowledge-bases/{knowledgeBaseId}/schemas/{schemaId}/attach`, including idempotent repeated attach.
- [x] 3.4 Add negative coverage for unknown knowledge base on create, unknown knowledge base on attach, and unknown schema on attach.
- [x] 3.5 Add regression coverage that create without `knowledgeBaseId` remains global and does not appear in an unrelated KB schema list.

## 4. Verification

- [x] 4.1 Run the focused schema and knowledge-base integration tests.
- [x] 4.2 Run `./mvnw test`.
- [x] 4.3 Run `graphify update .` after implementation changes.
