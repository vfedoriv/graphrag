## 1. Exception And Response Contract

- [x] 1.1 Extend query rejection exception model to carry validation error message list.
- [x] 1.2 Update global exception handler mapping for query rejection to include validation error text in response payload.
- [x] 1.3 Preserve existing top-level summary message (`Query validation failed`) and HTTP status behavior.

## 2. Service Logging And Propagation

- [x] 2.1 Update `CypherExecutionService` to propagate all validation errors when throwing query rejection.
- [x] 2.2 Add rejection log summary that includes error count and sanitized validation error message text.
- [x] 2.3 Ensure logging changes do not emit unsanitized user-controlled values.

## 3. Tests

- [x] 3.1 Add/adjust unit tests for single-error validation rejection payload contents.
- [x] 3.2 Add/adjust unit tests for multiple-error validation rejection payload ordering and completeness.
- [x] 3.3 Add/adjust controller/integration tests asserting response includes validation error text for failed execute requests.

## 4. Verification

- [x] 4.1 Run targeted tests for query validation/execution/error handling.
- [x] 4.2 Run `./mvnw test`.
- [x] 4.3 Run `openspec validate improve-query-validation-error-reporting --strict`.
