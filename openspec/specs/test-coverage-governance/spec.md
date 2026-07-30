# test-coverage-governance Specification

## Purpose
Defines expectations for auditing existing test coverage and adding missing unit or integration tests for critical behavior.

## Requirements
### Requirement: Coverage audit identifies critical gaps
The project SHALL maintain an implementation-oriented coverage audit that compares existing tests with critical production behavior across controllers, services, graph extraction and writing, schema handling, query validation and execution, storage, configuration, and observability.

#### Scenario: Existing tests are reviewed before adding new tests
- **WHEN** missing tests are being planned
- **THEN** the audit MUST identify the production surfaces already covered and the high-risk paths that still need focused tests

#### Scenario: Low-value coverage is excluded
- **WHEN** a production path has only trivial data accessors, framework-generated behavior, or duplicate coverage through a stronger test
- **THEN** the audit MUST avoid adding redundant tests for that path

### Requirement: Unit tests cover deterministic isolated behavior
The project SHALL add unit tests for missing deterministic behavior that can be validated without Spring context startup, Neo4j, file-system persistence, or network access.

#### Scenario: Helper or service branch lacks coverage
- **WHEN** a deterministic branch in a helper or service contains validation, normalization, parsing, safety, or error-mapping logic without direct coverage
- **THEN** a unit test MUST be added or extended to cover the branch and its expected outcome

#### Scenario: AI-dependent behavior is tested
- **WHEN** a unit test covers behavior that depends on an AI client
- **THEN** the AI client MUST be mocked or faked so the test is deterministic and does not require provider credentials

### Requirement: Integration tests cover wired component behavior
The project SHALL add integration tests for missing behavior whose risk is primarily in Spring wiring, request validation, controller contracts, Neo4j persistence, multipart handling, or transaction boundaries.

#### Scenario: Persistence behavior lacks end-to-end verification
- **WHEN** graph, schema, document, query, or knowledge-base behavior depends on Neo4j persistence semantics
- **THEN** an integration test MUST verify the behavior using the existing Testcontainers-backed test pattern

#### Scenario: HTTP contract behavior lacks verification
- **WHEN** an API endpoint has validation, error handling, status code, or response-shape behavior that is not covered by controller or MVC tests
- **THEN** a controller or MVC integration test MUST verify the public contract

### Requirement: Test suite remains deterministic and maintainable
The project SHALL keep added tests deterministic, isolated, readable, aligned with existing test conventions, and correctly classified for full and fast execution.

#### Scenario: Full tests are executed
- **WHEN** `./mvnw test` runs
- **THEN** the complete unit and integration regression suite MUST pass without requiring external AI provider credentials

#### Scenario: Fast tests are executed
- **WHEN** `./mvnw test -Pfast` runs
- **THEN** deterministic tests that do not require Testcontainers MUST execute
- **AND** Testcontainers-backed integration tests MUST be excluded by an explicit shared classification

#### Scenario: Test data is reusable
- **WHEN** a new test needs schemas, documents, embeddings, or graph extraction responses
- **THEN** it MUST reuse existing fixtures or add minimal focused fixtures under `src/test/resources`

#### Scenario: Integration coverage uses shared infrastructure
- **WHEN** a new integration test requires the normal application PostgreSQL or Neo4j service
- **THEN** it MUST use the shared application-integration fixture and explicit state cleanup
- **AND** it MUST use an independent container only when fresh-server lifecycle is the behavior under test

### Requirement: Operational privacy and documentation alignment have regression coverage
The project SHALL test content-safe application logging, observability/log separation, test logging configuration, and build-backed contributor documentation facts.

#### Scenario: Sensitive content is supplied to a workflow
- **WHEN** a deterministic test supplies recognizable document, prompt, query, or model-response content
- **THEN** normal captured application logs do not contain that content
- **AND** required operational metadata remains available

#### Scenario: Documentation fact is changed
- **WHEN** a documented stack or shared configuration fact diverges from the canonical build configuration
- **THEN** the regression check fails with the inconsistent documentation location

### Requirement: Polyglot persistence invariants have integration coverage
The test suite SHALL verify shared-server database isolation, role identity, Flyway placement, transaction routing, store-specific rollback, cross-store recovery, graph scoping, and graph purity.

#### Scenario: Shared PostgreSQL isolation tests run
- **WHEN** GraphRAG provisioning, Flyway, and reset tests execute against a server containing seeded Langfuse data
- **THEN** all GraphRAG objects remain in its database/schema
- **AND** Langfuse sentinel data remains unchanged

#### Scenario: Transaction routing tests run
- **WHEN** relational and graph failure paths are exercised
- **THEN** rollback affects only the selected store
- **AND** the explicit SDN template supports repository queries

#### Scenario: Full-flow tests run
- **WHEN** the canonical test suite completes without external AI credentials
- **THEN** API behavior, recovery semantics, KB isolation, and absence of retired graph structures are verified

### Requirement: Datasource precedence has focused configuration coverage
The test suite MUST verify that Docker Compose service-connection discovery cannot override GraphRAG's explicit datasource and that local datasource defaults remain aligned with PostgreSQL provisioning defaults.

#### Scenario: Compose routing regression test runs
- **WHEN** repository configuration is tested
- **THEN** the shared Langfuse PostgreSQL service has the Spring Boot ignore label
- **AND** application and provisioning defaults agree on `graphrag / graphrag / app`
- **AND** the test does not require a running database, migration simulation, or external AI credentials
