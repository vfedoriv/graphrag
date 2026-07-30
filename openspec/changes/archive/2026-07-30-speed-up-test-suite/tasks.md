## 1. Baseline and Measurement

- [x] 1.1 Add a repository-supported performance report that summarizes successful Surefire test counts, aggregate or wall time, slowest tests, Spring context starts, and PostgreSQL and Neo4j container starts.
- [x] 1.2 Record the intended inventory for comparison. Per user direction, do not rerun the previous revision; retain only current-version run artifacts and statistics.
- [x] 1.3 Add focused checks for the performance report so stale Surefire files, failed runs, and fresh-server PostgreSQL containers are distinguished from normal application-integration lifecycle counts.

## 2. Test Classification and Fast Feedback

- [x] 2.1 Introduce a shared JUnit integration classification, preferably through composed test annotations, and apply it to every Spring, Testcontainers, provisioning, startup, and end-to-end test that requires Docker-backed services.
- [x] 2.2 Add a Maven `fast` profile that excludes the shared integration classification while leaving default `./mvnw test` selection unchanged and sequential.
- [x] 2.3 Add build-backed regression checks that the fast lane includes architecture, contract, configuration, unit, and mocked component tests, excludes Docker-backed tests, and that the full lane retains the intended inventory.
- [x] 2.4 Initialize the ArchUnit production-class import once per test class and verify every architecture rule still executes in both lanes.

## 3. Explicit Integration-Test Isolation

- [x] 3.1 Centralize idempotent PostgreSQL, Neo4j, document-storage, and applicable runtime-setting cleanup into a reusable integration-test lifecycle component without weakening store-specific assertions.
- [x] 3.2 Replace context-lifetime-dependent counters, latches, queues, and scripted AI client state with scenario-local behavior or explicit reset hooks.
- [x] 3.3 Add isolation regression coverage that runs representative mutating tests repeatedly and in varied order and proves that only declared fixtures and required seeded defaults remain.
- [x] 3.4 Remove `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` from graph cleanup and provenance tests after their database, filesystem, and fake-client reset coverage passes.

## 4. JVM-Scoped Shared Containers

- [x] 4.1 Implement a lazy JVM-scoped fixture that starts and exposes one normal application-integration PostgreSQL container and one normal application-integration Neo4j container and stops them at Surefire JVM shutdown.
- [x] 4.2 Implement the Spring Boot 4.1-compatible connection bridge for all integration context families and verify that Spring context closure does not stop the shared containers.
- [x] 4.3 Migrate application integration tests away from context-owned container beans while retaining independently managed containers for fresh-server provisioning and startup tests.
- [x] 4.4 Add lifecycle regression checks proving that distinct and evicted Spring contexts reuse the same normal container identities and that fresh-server tests remain separate.

## 5. Spring Context Reuse

- [x] 5.1 Move common test auto-configuration exclusions and stable properties into shared test-profile configuration so equivalent tests produce compatible context-cache keys.
- [x] 5.2 Define and migrate to the required PostgreSQL-only, PostgreSQL-plus-Neo4j, full-MVC deterministic-AI, and processing/search deterministic-client configuration families.
- [x] 5.3 Audit PostgreSQL-only tests for implicit Neo4j dependencies, keep genuinely configuration-specific contexts separate, and ensure store-specific tests do not start unused services.
- [x] 5.4 Measure context cache behavior after migration and remove accidental one-off property or import differences without merging tests whose configuration difference is under test.

## 6. Verification and Contributor Guidance

- [x] 6.1 Run focused isolation, provisioning, startup, query-deadline, end-to-end, and profile-selection tests with the required escalated Testcontainers execution.
- [x] 6.2 Run `./mvnw test -Pfast` and verify that it succeeds without Docker or external AI credentials.
- [x] 6.3 Run three successful warm-image `./mvnw test` measurements, verify the same intended full inventory, at most one normal PostgreSQL and Neo4j start, and at least a 35 percent median improvement from the recorded baseline.
- [x] 6.4 Update overlapping test commands, lifecycle guidance, and performance-report usage in `README.md`, `AGENTS.md`, and `CLAUDE.md` where present, keeping shared facts synchronized.
- [x] 6.5 Run `graphify update .` after code and documentation changes and review the final diff for unrelated or generated-file noise.
