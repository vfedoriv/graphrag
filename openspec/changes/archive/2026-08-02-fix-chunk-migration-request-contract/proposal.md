## Why

Valid `CHUNK_STRATEGY_MIGRATION` requests currently fail at JSON deserialization when they omit the schema-specific `allDocuments` property, because the shared backend request record declares that property as a primitive boolean. This contradicts the reason-specific migration contract and prevents the request from reaching migration validation or plan creation.

## What Changes

- Make the shared reprocessing-plan request boundary accept an omitted or explicit `null` `allDocuments` value.
- Keep `selection` authoritative for `CHUNK_STRATEGY_MIGRATION`; schema-only `allDocuments` input does not participate in migration selection.
- Preserve schema-activation selection behavior by treating only `Boolean.TRUE` as the all-documents choice and otherwise requiring a non-empty explicit document list.
- Add HTTP-boundary regression coverage that submits the production migration body without `allDocuments`, plus reason-specific compatibility coverage for schema activation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `chunk-strategy-reprocessing`: Define that chunk migration creation accepts its reason-specific request without the schema-only `allDocuments` property and uses `selection` as the migration selector.
- `schema-reprocessing-plans`: Clarify schema-activation selection semantics when the shared nullable `allDocuments` property is omitted or `null`.

## Impact

- Backend API DTO: `SchemaReprocessingDtos.CreatePlanRequest`.
- Reason-specific request validation and schema document selection in `SchemaReprocessingPlanService`.
- MockMvc/controller-boundary tests for `POST /api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans` and existing direct DTO construction in service tests.
- No persistence, frontend, endpoint, or response-contract changes.
