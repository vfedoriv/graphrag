## Why

The full Maven test suite currently takes about 4 minutes 21 seconds on the measured development environment, with context-owned Testcontainers accounting for most of the runtime. This delays local feedback and will worsen as integration coverage grows unless container lifecycle, context reuse, and test-lane conventions become explicit.

## What Changes

- Share the normal integration-test PostgreSQL and Neo4j containers across Spring application contexts within one Maven test JVM while retaining explicit data isolation between tests.
- Remove unnecessary method-level Spring context invalidation and replace it with deterministic reset of database, filesystem, and stateful test-double state.
- Standardize integration-test configuration families so equivalent tests reuse Spring's context cache and store-specific tests do not start services they do not need.
- Add a fast Maven test lane for deterministic tests that do not require Testcontainers while preserving `./mvnw test` as the complete credential-free verification command.
- Initialize the ArchUnit production-class import once per test class instead of once per test method.
- Add repeatable timing and lifecycle measurements that report full-suite duration, Spring context starts, and container starts before and after the change.
- Keep integration tests sequential until their shared database and filesystem state is isolated sufficiently for supported parallel execution.

## Capabilities

### New Capabilities

- `test-suite-execution-performance`: Defines shared integration infrastructure, deterministic test isolation, fast-feedback execution, context reuse, and performance verification expectations.

### Modified Capabilities

- `test-coverage-governance`: Clarifies that faster execution must retain the complete credential-free regression coverage of `./mvnw test` and that a fast lane may select only deterministic non-container tests.

## Impact

- Test infrastructure under `src/test/java`, including `TestcontainersConfiguration`, shared cleanup utilities, integration-test annotations/configurations, stateful test doubles, and architecture tests.
- Maven Surefire configuration and contributor test commands in `pom.xml`, `README.md`, `AGENTS.md`, and `CLAUDE.md` where overlapping guidance exists.
- No production API, persistence schema, runtime behavior, or external dependency contract changes.
- PostgreSQL provisioning tests that intentionally require fresh servers remain independently containerized and outside the shared application-integration fixture.
