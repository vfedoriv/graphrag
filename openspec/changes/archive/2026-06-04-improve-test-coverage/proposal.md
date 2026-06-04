## Why

The project has meaningful unit and integration coverage, but coverage gaps can still hide regressions in critical graph ingestion, schema validation, query validation, storage, and observability paths. A deliberate coverage audit is needed now so missing tests are added where they protect production behavior instead of simply increasing test count.

## What Changes

- Audit existing unit and integration tests against the current service, controller, repository, parser, validation, and observability surfaces.
- Identify high-value uncovered paths, especially error handling, boundary validation, schema-constrained extraction, graph write cleanup, query safety, and persistence workflows.
- Add focused unit tests for deterministic service/helper behavior where integration tests are unnecessary.
- Add or extend integration tests for cross-component behavior that depends on Spring wiring, Neo4j persistence, multipart upload, or controller contracts.
- Keep tests deterministic by mocking AI clients and using existing Testcontainers patterns for Neo4j-backed flows.
- Do not change production APIs or behavior unless a test exposes an existing defect that must be fixed.

## Capabilities

### New Capabilities
- `test-coverage-governance`: Defines expectations for auditing existing test coverage and adding missing unit or integration tests for critical behavior.

### Modified Capabilities

## Impact

- Affected code: primarily `src/test/java` and `src/test/resources`, with narrowly scoped production fixes only if newly added tests expose a defect.
- APIs: no intended API contract changes.
- Dependencies: no new runtime dependencies; test-only dependency changes should be avoided unless clearly necessary.
- Systems: local Maven test execution, Testcontainers-backed Neo4j integration tests, and mocked AI-provider test paths.
