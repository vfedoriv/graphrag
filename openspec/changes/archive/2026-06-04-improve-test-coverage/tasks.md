## 1. Coverage Audit

- [x] 1.1 Inventory current production classes under `src/main/java` and map each area to existing tests under `src/test/java`.
- [x] 1.2 Identify high-value missing branch, error, boundary, persistence, controller-contract, and observability coverage gaps.
- [x] 1.3 Exclude trivial accessors, enum constants, duplicate scenarios, and framework behavior from the planned test additions.

## 2. Unit Test Additions

- [x] 2.1 Add or extend unit tests for uncovered deterministic helper and service branches found during the audit.
- [x] 2.2 Add or extend unit tests for uncovered validation, normalization, parsing, cleanup, query-safety, or error-mapping behavior found during the audit.
- [x] 2.3 Ensure tests for AI-dependent paths use mocks or fakes and require no external provider credentials.

## 3. Integration Test Additions

- [x] 3.1 Add or extend Neo4j-backed integration tests for uncovered persistence, transaction, or repository behavior found during the audit.
- [x] 3.2 Add or extend controller or MVC integration tests for uncovered HTTP status, validation, error, multipart, or response-shape contracts found during the audit.
- [x] 3.3 Reuse existing Testcontainers configuration, test fixtures, and mocked AI-client patterns for all new integration coverage.

## 4. Verification

- [x] 4.1 Run targeted Maven tests for the classes added or changed.
- [x] 4.2 Run `./mvnw test` and confirm the full suite passes without external AI provider credentials.
- [x] 4.3 Document the coverage gaps addressed and any intentionally deferred low-value areas in the implementation summary.
