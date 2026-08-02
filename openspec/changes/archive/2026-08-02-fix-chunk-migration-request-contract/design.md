## Context

`POST /api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans` uses one request record for two reason-specific operations. Schema activation selects documents with `allDocuments` or `documentIds`; chunk migration selects them with `selection` and requires `expectedChunkerRevision`. The record currently declares `allDocuments` as primitive `boolean`, so strict primitive-null deserialization rejects a chunk migration body that correctly omits this unrelated property. The rejection occurs before controller or service dispatch.

Existing service tests instantiate the Java record directly and pass `false`, so they do not exercise the failing JSON boundary. Existing schema requests must remain compatible while the boundary is corrected.

## Goals / Non-Goals

**Goals:**

- Allow valid chunk migration JSON to omit or explicitly null `allDocuments`.
- Keep request interpretation reason-specific and preserve existing schema activation behavior.
- Detect regressions at the HTTP/Jackson boundary, not only through direct service calls.

**Non-Goals:**

- Split the shared endpoint or introduce a new persistence model.
- Change chunk migration selection, preview, revision validation, or plan execution.
- Change the frontend payload, which already follows the intended migration contract.
- Redesign the complete reprocessing request as a polymorphic DTO hierarchy in this fix.

## Decisions

### Use a nullable wrapper at the shared JSON boundary

Change the canonical `CreatePlanRequest.allDocuments` component from `boolean` to `Boolean`. Jackson can then represent both an omitted property and explicit JSON `null`, allowing the service to dispatch using `reason` before applying reason-specific validation. The existing schema-oriented convenience constructor may continue accepting primitive `boolean` so Java call sites remain concise and source compatible.

Alternative: make the frontend always send `allDocuments: false`. Rejected because it makes a schema-specific property mandatory in the migration branch, contradicts the existing migration contract, and leaves other clients exposed to the same boundary failure.

Alternative: introduce polymorphic reason-specific request types immediately. Rejected as disproportionate for this compatibility fix because it changes OpenAPI/Jackson modeling and a shared endpoint contract beyond the defect.

### Interpret `allDocuments` only in schema activation selection

For `SCHEMA_ACTIVATION`, derive the all-documents choice with `Boolean.TRUE.equals(request.allDocuments())`, then preserve the existing exclusive choice between all documents and a non-empty `documentIds` list. Omitted, `null`, and `false` therefore all mean “not the all-documents choice”; they remain valid only when an explicit non-empty owned document list is present.

For `CHUNK_STRATEGY_MIGRATION`, continue using `selection` and the migration classifier. Ignore `allDocuments` if a legacy or generic client supplies it. Ignoring it is preferable to rejection because existing migration-side Java construction and generic clients may still serialize `false`, while the property has no authority in this branch.

### Test the exact JSON shape through MockMvc

Add a focused controller/HTTP-boundary test that posts `reason`, `selection`, `processingOptions: null`, and `expectedChunkerRevision` without `allDocuments`. Stub the service response and capture the request passed by the controller so the test proves deserialization completes and preserves `allDocuments == null` before downstream migration logic.

Retain or extend service-level tests to prove schema selection uses null-safe Boolean handling and migration behavior remains based on `selection`. This separates boundary regression coverage from domain validation coverage.

## Risks / Trade-offs

- [Nullable state leaks into schema selection and causes unboxing failures] → Centralize interpretation with `Boolean.TRUE.equals` and cover omitted/null schema requests.
- [Ignoring an irrelevant migration property hides confused clients] → Keep `selection` as the only normative migration selector and document the compatibility behavior; conflicting schema fields cannot change selected migration documents.
- [A mocked controller test proves binding but not full migration creation] → Assert service delegation with the captured DTO and retain existing service tests for migration validation/creation behavior.
- [Record constructor call sites fail to compile after wrapper conversion] → Preserve the primitive convenience constructor and update canonical-constructor call sites only where necessary.
