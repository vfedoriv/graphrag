## 1. API Contract Update

- [x] 1.1 Update the schema-by-id response DTO/model to include a `content` field for persisted schema definition text
- [x] 1.2 Ensure `SchemaController#getSchema()` response mapping includes the new `content` field without removing existing fields

## 2. Service and Mapping Validation

- [x] 2.1 Verify the schema retrieval service path provides persisted schema content to the controller mapping path
- [x] 2.2 Add or adjust null/empty-content handling in mapping logic to keep serialization behavior consistent

## 3. Automated Verification

- [x] 3.1 Add or update controller/service tests asserting `GET /api/v1/schemas/{schemaId}` returns `content` for existing schemas
- [x] 3.2 Confirm existing not-found behavior test coverage remains valid for missing schema ids
