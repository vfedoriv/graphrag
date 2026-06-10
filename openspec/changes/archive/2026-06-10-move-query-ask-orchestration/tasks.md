## 1. Ask Workflow Service

- [x] 1.1 Add `QueryAskService` to coordinate the one-shot generate, validate, execute flow
- [x] 1.2 Move query workflow observation setup and high-cardinality attribute recording from `QueryController.ask` into `QueryAskService`
- [x] 1.3 Preserve invalid generated query handling so execution is not called when validation fails
- [x] 1.4 Preserve `QueryAskResponse` assembly with generated and execution results

## 2. Controller Refactor

- [x] 2.1 Update `QueryController` to delegate `/queries/ask` requests to `QueryAskService`
- [x] 2.2 Remove direct ask workflow dependencies from `QueryController` that are no longer needed
- [x] 2.3 Keep request logging and endpoint annotations unchanged

## 3. Verification

- [x] 3.1 Add `QueryAskServiceTest` for success, invalid generated query, and runtime failure observation behavior
- [x] 3.2 Update `QueryControllerTest` to verify ask delegation and response propagation
- [x] 3.3 Run `./mvnw test`
