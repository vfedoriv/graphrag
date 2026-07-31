# test-suite-execution-performance Specification

## Purpose
Defines deterministic, shared-container test execution and repeatable full-suite performance measurement.

## Requirements

### Requirement: Application integration containers are shared within a test JVM
The test suite SHALL start at most one normal application-integration PostgreSQL container and one normal application-integration Neo4j container in a Maven test JVM, and SHALL make those running services available to every compatible Spring test context in that JVM.

#### Scenario: Distinct Spring contexts execute in one full suite
- **WHEN** multiple application integration-test classes require different Spring context configurations during `./mvnw test`
- **THEN** the classes MUST connect to the same normal application-integration PostgreSQL and Neo4j container instances
- **AND** closing or evicting one Spring context MUST NOT stop those shared containers

#### Scenario: Fresh-server behavior is verified
- **WHEN** a PostgreSQL provisioning or startup test explicitly verifies behavior against a fresh server
- **THEN** that test MUST use an independently managed container
- **AND** it MUST NOT mutate or restart the shared application-integration PostgreSQL container

### Requirement: Integration context families declare complete startup dependencies
Each Spring integration-test configuration family SHALL either provide every persistence service used by its application startup runners or explicitly disable only the startup work for stores outside that family's scope.

#### Scenario: PostgreSQL-only integration context starts
- **WHEN** a relational integration test starts with the PostgreSQL-only configuration family
- **THEN** the context MUST start without a Neo4j container or operator-managed Neo4j service
- **AND** graph schema initialization MUST NOT attempt a connection
- **AND** unexpected graph data-plane use by the test MUST still fail visibly

#### Scenario: Full-store integration context starts
- **WHEN** an integration test uses the full-store configuration family
- **THEN** the context MUST receive both shared PostgreSQL and Neo4j connection details
- **AND** real graph schema initialization MUST execute against the shared Neo4j container

### Requirement: Shared integration infrastructure preserves deterministic isolation
Every application integration test SHALL begin and end with the PostgreSQL records, Neo4j graph artifacts, local document files, runtime overrides, and stateful test doubles in the baseline state required by the suite, including when the test is the final method executed before a Spring context transition.

#### Scenario: A test follows a mutating integration test
- **WHEN** a test executes after another test that wrote relational, graph, filesystem, runtime-setting, or fake-client state
- **THEN** the later test MUST observe only its declared fixtures and baseline seeded state

#### Scenario: A Spring context closes after its final test
- **WHEN** the final test method in a cached Spring context completes
- **THEN** shared mutable state MUST be restored before another context can execute application startup runners against the shared containers
- **AND** cleanup MUST NOT depend on a subsequent test method's setup callback

#### Scenario: Stateful test double controls a retry sequence
- **WHEN** a deterministic fake uses counters, latches, queues, or scripted responses
- **THEN** its mutable state MUST be reset explicitly between tests
- **AND** Spring context recreation MUST NOT be required solely to reset that state

#### Scenario: Test cleanup fails
- **WHEN** baseline restoration cannot complete
- **THEN** the suite MUST fail with cleanup diagnostics
- **AND** it MUST NOT silently continue with contaminated shared state

### Requirement: Spring test contexts are reused intentionally
Integration tests SHALL use shared configuration families and SHALL invalidate a cached Spring context only when a test mutates context-owned state that cannot be restored explicitly.

#### Scenario: Tests require the same application configuration
- **WHEN** multiple test classes require the same stores, test profile, auto-configuration exclusions, and deterministic test beans
- **THEN** their merged Spring configuration MUST be cache-compatible

#### Scenario: A test currently uses method-level context invalidation
- **WHEN** database, filesystem, and fake-client cleanup can restore its required baseline
- **THEN** the test MUST use explicit cleanup instead of `@DirtiesContext(BEFORE_EACH_TEST_METHOD)`

### Requirement: Fast and full verification lanes are available
The build SHALL provide an additive fast test lane that excludes Testcontainers-backed integration tests, while the default Maven test command SHALL continue to execute the complete regression suite.

#### Scenario: Developer requests fast feedback
- **WHEN** `./mvnw test -Pfast` executes
- **THEN** deterministic unit, contract, configuration, architecture, and mocked component tests MUST run
- **AND** tests requiring PostgreSQL, Neo4j, Docker, or external AI credentials MUST NOT run

#### Scenario: Developer or CI requests complete verification
- **WHEN** `./mvnw test` executes without the fast profile
- **THEN** both fast-lane and integration tests MUST run
- **AND** the suite MUST require no external AI provider credentials

### Requirement: Integration tests remain sequential until independently isolated
The build SHALL NOT enable parallel execution for shared-container integration tests unless each concurrent worker has isolated relational, graph, filesystem, and mutable test-double state.

#### Scenario: Shared application-integration containers are active
- **WHEN** integration tests use cleanup operations that affect the shared database or document root
- **THEN** those integration tests MUST execute sequentially

### Requirement: Test-suite performance is measured repeatably
The project SHALL provide a repeatable measurement that reports full-suite wall time, Spring context-start count, and PostgreSQL and Neo4j container-start counts from a successful run.

#### Scenario: Optimization is validated
- **WHEN** the optimized suite is compared with the recorded 260.7-second baseline on the same host with container images warm
- **THEN** the median of three successful full-suite runs MUST improve by at least 35 percent
- **AND** the report MUST show no more than one normal application-integration start for each shared database container

#### Scenario: Coverage equivalence is checked
- **WHEN** before-and-after performance results are compared
- **THEN** the optimized full suite MUST execute the same intended test inventory or document every deliberate inventory change
