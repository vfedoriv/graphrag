## Context

The measured full suite produced 109 current Surefire reports containing 491 tests and approximately 260.7 seconds of aggregate suite time. Integration tests accounted for about 255 seconds, while all other tests accounted for about 7.3 seconds.

The application integration pattern imports `TestcontainersConfiguration`, which declares PostgreSQL and Neo4j as Spring beans. Their lifecycle is therefore coupled to each cached application context. The measured run started 22 Spring contexts, 22 Neo4j containers, and 26 PostgreSQL containers. Neo4j startup alone consumed about 144.9 seconds, and total context startup consumed about 186.6 seconds.

Two three-method integration classes use `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` despite already deleting graph, relational, and filesystem state. They account for eight context and container starts and about 86 seconds of suite time. Other distinct contexts arise from test-specific imports and property sources. Parallel execution is currently unsafe because tests clear shared stores, use one document root, and contain mutable static or bean-scoped test controls.

## Goals / Non-Goals

**Goals:**

- Reduce the median warm-image full-suite time by at least 35 percent without reducing intended coverage.
- Start one normal application-integration PostgreSQL container and one normal application-integration Neo4j container per Maven test JVM.
- Preserve deterministic isolation through explicit cleanup and reset contracts.
- Preserve `./mvnw test` as complete verification and add `./mvnw test -Pfast` for non-container feedback.
- Reduce avoidable Spring context cache fragmentation and repeated ArchUnit scanning.
- Make performance and lifecycle counts repeatable and reviewable.

**Non-Goals:**

- Changing production APIs, persistence schemas, application runtime configuration, or AI-provider behavior.
- Sharing containers across Maven JVMs, developer sessions, or CI jobs.
- Using Testcontainers reusable-container mode.
- Enabling parallel shared-container integration tests in this change.
- Removing fresh-container provisioning, startup, query-deadline, end-to-end, or persistence-boundary coverage merely to improve timing.

## Decisions

### Use a JVM-scoped application integration fixture

A static, lazily initialized fixture will own the normal PostgreSQL and Neo4j containers for the lifetime of the Surefire JVM. Spring tests will consume stable connection details through a shared integration-test bootstrap mechanism rather than declaring container instances as context-owned beans. JVM shutdown will stop the fixture.

This keeps container lifetime independent of Spring context eviction while retaining disposable infrastructure per Maven invocation.

Alternatives considered:

- **Context-owned `@ServiceConnection` beans:** This is the current design and restarts containers with every distinct or dirty context.
- **Testcontainers reusable containers:** This leaks lifecycle beyond one test JVM, requires developer-level opt-in, and makes stale-state failures harder to reproduce in CI.
- **One container pair per test class:** This provides strong isolation but preserves the dominant startup cost.

Tests that verify PostgreSQL initialization, provisioning, reset, backup, restore, or startup against a new server will retain independent containers because freshness is part of their contract.

### Centralize explicit isolation instead of recreating infrastructure

A shared integration-test lifecycle component will restore baseline state before each mutating test. It will compose the existing relational cleanup order, Neo4j cleanup, document-storage cleanup, runtime-setting cleanup where relevant, and reset hooks for deterministic test doubles.

Cleanup must be idempotent and must complete before the next test begins. Tests that need seeded defaults will restore or recreate them after cleanup. Stateful fake clients will expose scenario-local scripts or explicit reset operations rather than relying on Spring context recreation.

The two method-level `@DirtiesContext` uses will be removed only after their retry counters and other mutable state are proven independent across reordered test execution.

Alternatives considered:

- **Transactional rollback for every test:** PostgreSQL and Neo4j transactions cannot provide a single cross-store rollback, and filesystem and asynchronous state would remain.
- **Database recreation per method:** It is simpler conceptually but repeats the measured startup bottleneck.

### Define a small set of composed integration-test families

Common auto-configuration exclusions and stable properties will move into shared test-profile configuration. Composed annotations or base bootstrap classes will define a small number of cache-compatible families:

1. PostgreSQL-only integration.
2. PostgreSQL plus Neo4j integration.
3. Full MVC with deterministic AI.
4. Processing/search with deterministic embedding or extraction clients.

Test-specific properties and beans remain allowed when behavior genuinely depends on them. The purpose is to remove accidental differences, not force incompatible tests into one context. Store-specific tests will not start unused infrastructure.

### Add a tag-driven fast Maven profile while preserving default semantics

Testcontainers-backed integration tests will receive one shared JUnit integration classification, preferably through the composed integration annotations. Surefire's `fast` profile will exclude that classification. The default configuration will exclude nothing, preserving the documented meaning of `./mvnw test`.

Architecture, configuration, contract, unit, and mocked component tests remain in the fast lane. Fresh-container provisioning tests are integration-classified even when they do not use Spring.

Moving integration tests to Failsafe and changing full verification to `verify` was rejected for this change because it would silently change an established contributor and CI command.

### Keep execution sequential

No JUnit or Surefire parallel mode will be enabled for integration tests. Shared cleanup uses database-wide deletes and a shared document root, so concurrent execution would create races. Forked parallelism would also multiply container memory and require per-fork PostgreSQL databases, Neo4j databases or label namespaces, filesystem roots, and deterministic fake state.

Parallel execution can be proposed separately after those isolation boundaries exist.

### Reuse the ArchUnit class import

The production `JavaClasses` import will be initialized once per test class, using static initialization or an equivalent `@BeforeAll` lifecycle. All architecture rules will continue to run in both fast and full lanes.

### Measure lifecycle counts as well as time

A repository script or equivalent build-supported report will parse a successful Surefire run and report:

- Wall-clock or aggregate suite duration.
- Executed suite and test counts.
- Spring application-context start count.
- Normal and fresh PostgreSQL container-start counts.
- Neo4j container-start count.
- Slowest suites and test methods.

Acceptance will compare the median of three warm-image runs on the same host against the recorded 260.7-second baseline. Container-count assertions provide a stable structural signal even when elapsed time varies between hosts.

## Risks / Trade-offs

- **[State leakage between tests]** → Centralize idempotent cleanup, reset stateful fakes, run tests in varied order during validation, and retain focused isolation regression tests.
- **[A shared container fails and affects the remainder of the suite]** → Fail fast with clear fixture health diagnostics; a new Maven invocation recreates the fixture.
- **[Static lifecycle conflicts with Spring connection customization]** → Keep one bootstrap path for connection properties and verify every context family against both stores before removing the old beans.
- **[Context consolidation hides configuration-specific behavior]** → Consolidate only identical requirements and retain dedicated contexts for tests whose property or bean differences are under test.
- **[Fast profile drifts and omits deterministic tests]** → Classify the container boundary centrally and add a build-backed test that checks profile behavior.
- **[Performance threshold is noisy]** → Use warm images, the same host, three successful runs, a median comparison, and container/context counts alongside duration.
- **[Shared containers increase retained memory during the suite]** → Keep one pair only, stop it at JVM shutdown, and avoid parallel forks.

## Migration Plan

1. Add performance reporting and record a fresh three-run baseline before lifecycle changes.
2. Introduce integration classification and the additive fast profile; verify default test inventory is unchanged.
3. Cache the ArchUnit import once.
4. Add explicit fake-state reset and isolation checks, then remove method-level `@DirtiesContext`.
5. Introduce the JVM-scoped container fixture and migrate application integration contexts incrementally.
6. Separate PostgreSQL-only context families and consolidate equivalent full-context configurations.
7. Run isolation-focused tests repeatedly and in varied order, then run three full warm-image measurements.
8. Update overlapping contributor commands and lifecycle guidance in `README.md`, `AGENTS.md`, and `CLAUDE.md`.

Rollback is limited to test infrastructure: restore context-owned container beans and the previous test annotations/profile configuration. No production or persisted data migration is involved.

## Open Questions

- Whether the connection bridge is clearest as a shared `@DynamicPropertySource` base, a Spring test context customizer, or a small composed bootstrap annotation should be decided during the first fixture task based on Spring Boot 4.1 service-connection compatibility.
- PostgreSQL-only test classification may expose previously implicit Neo4j bean dependencies; those dependencies must be audited before finalizing the context families.
