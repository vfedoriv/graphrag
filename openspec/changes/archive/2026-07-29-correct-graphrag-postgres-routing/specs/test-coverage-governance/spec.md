## ADDED Requirements

### Requirement: Datasource precedence has focused configuration coverage
The test suite MUST verify that Docker Compose service-connection discovery cannot override GraphRAG's explicit datasource and that local datasource defaults remain aligned with PostgreSQL provisioning defaults.

#### Scenario: Compose routing regression test runs
- **WHEN** repository configuration is tested
- **THEN** the shared Langfuse PostgreSQL service has the Spring Boot ignore label
- **AND** application and provisioning defaults agree on `graphrag / graphrag / app`
- **AND** the test does not require a running database, migration simulation, or external AI credentials
