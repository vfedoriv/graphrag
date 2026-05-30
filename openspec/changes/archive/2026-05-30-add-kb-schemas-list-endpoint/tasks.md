## 1. API Contract and Routing

- [x] 1.1 Add a new `GET` route in `SchemaController` for knowledge-base-scoped schema listing under `/api/v1`.
- [x] 1.2 Define request parameter binding and validation for the knowledge base identifier.
- [x] 1.3 Ensure endpoint response type and status codes align with existing schema API conventions.

## 2. Service and Repository Implementation

- [x] 2.1 Add service-layer method to retrieve schemas associated with a specific knowledge base.
- [x] 2.2 Implement repository query logic that filters schemas by knowledge base association.
- [x] 2.3 Add knowledge base existence handling to distinguish unknown knowledge base from empty association results.

## 3. Error Handling and Response Mapping

- [x] 3.1 Return `ProblemDetail` not-found behavior for unknown knowledge base identifiers.
- [x] 3.2 Reuse existing schema DTO mapping so list items remain consistent with current schema responses.
- [x] 3.3 Verify behavior for valid knowledge base with zero associated schemas returns `200` and an empty list.

## 4. Test Coverage

- [x] 4.1 Add controller/service test for successful retrieval when schemas are associated.
- [x] 4.2 Add test for valid knowledge base with no associated schemas.
- [x] 4.3 Add test for unknown knowledge base returning not-found `ProblemDetail`.
