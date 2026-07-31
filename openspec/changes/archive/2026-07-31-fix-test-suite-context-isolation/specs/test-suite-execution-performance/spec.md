## ADDED Requirements

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

## MODIFIED Requirements

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
