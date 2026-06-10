## ADDED Requirements

### Requirement: Architecture rules run with the test suite
The system SHALL include automated architecture boundary tests that run as part of the normal Maven test suite.

#### Scenario: Maven tests run
- **WHEN** the normal test command runs
- **THEN** architecture boundary tests execute with the rest of the test suite

### Requirement: Controller boundaries are enforced
Architecture tests SHALL prevent controllers from directly depending on repositories or direct Neo4j database clients.

#### Scenario: Controller dependency is checked
- **WHEN** architecture tests inspect controller classes
- **THEN** any direct dependency from controllers to repository classes or `Neo4jClient` fails the architecture test

### Requirement: Domain boundaries are enforced
Architecture tests SHALL prevent persistence/domain node classes from depending on controllers, services, DTOs, repositories, or application workflow components.

#### Scenario: Domain dependency is checked
- **WHEN** architecture tests inspect domain classes
- **THEN** dependencies from domain classes to controller, service, DTO, repository, or workflow packages fail the architecture test

### Requirement: Direct Neo4j access is governed
Architecture tests SHALL document and constrain which application or persistence components may use `Neo4jClient` directly.

#### Scenario: Direct Neo4j dependency is checked
- **WHEN** architecture tests inspect production classes
- **THEN** direct `Neo4jClient` usage outside the approved allowlist fails the architecture test

### Requirement: Transitional exceptions are explicit
Architecture tests SHALL keep any current exceptions to the target architecture explicit and named so they can be reviewed and removed over time.

#### Scenario: Existing exception is required
- **WHEN** a current dependency does not yet match the target boundary
- **THEN** the architecture test documents the exception by class or package rather than silently allowing the dependency pattern everywhere
