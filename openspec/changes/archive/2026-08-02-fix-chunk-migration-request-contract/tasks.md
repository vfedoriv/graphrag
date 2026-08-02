## 1. Request Contract

- [x] 1.1 Change the canonical `CreatePlanRequest.allDocuments` component to nullable `Boolean` while preserving the schema-oriented primitive convenience constructor and compatible Java call sites.
- [x] 1.2 Update schema-activation document selection to use `Boolean.TRUE.equals`, retain the exclusive all-documents versus explicit-document rule, and avoid nullable Boolean unboxing.
- [x] 1.3 Confirm chunk migration creation continues to use `selection` exclusively and does not allow `allDocuments` to affect migration candidates.

## 2. Regression Coverage

- [x] 2.1 Add a MockMvc HTTP-boundary test that posts the exact migration request shape with `reason`, `selection`, `processingOptions: null`, and `expectedChunkerRevision` while omitting `allDocuments`, then asserts controller-to-service delegation succeeds with a null `allDocuments` value.
- [x] 2.2 Add coverage for explicit JSON `allDocuments: null` and legacy migration boolean values to verify migration selection remains authoritative.
- [x] 2.3 Add or update schema reprocessing service tests for `allDocuments=true`, explicit document IDs with omitted/null/false `allDocuments`, and rejection of missing or combined selection choices.

## 3. Verification

- [x] 3.1 Run the focused controller and `SchemaReprocessingPlanServiceTest` test classes, then run `./mvnw test -Pfast`.
- [x] 3.2 Run `graphify update .` after code changes and confirm the request DTO, controller boundary, service branches, and regression tests are represented in the refreshed graph.
