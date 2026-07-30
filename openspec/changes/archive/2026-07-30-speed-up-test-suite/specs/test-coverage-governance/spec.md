## MODIFIED Requirements

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

