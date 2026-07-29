## MODIFIED Requirements

### Requirement: Contributor and operator guidance reflects the final persistence topology
The system documentation SHALL consistently describe PostgreSQL as required operational storage, Neo4j as graph-only storage, shared-server isolation, supported profiles, provisioning, startup, and the current reset-only fresh-start commands.

#### Scenario: Shared guidance is updated
- **WHEN** persistence startup or reset documentation is changed
- **THEN** overlapping facts in `README.md`, `AGENTS.md`, and `CLAUDE.md` are synchronized in the same change
- **AND** commands use the Maven Wrapper and the implemented provisioning path

#### Scenario: Disposable GraphRAG state is reset
- **WHEN** the one-time routing correction is documented
- **THEN** guidance states that existing GraphRAG PostgreSQL and Neo4j data is intentionally discarded
- **AND** does not require backup, restore, quarantine, or migration procedures
- **AND** cleanup targets GraphRAG-owned state without deleting Langfuse-owned tables

## ADDED Requirements

### Requirement: Persistence guidance describes effective datasource precedence
Repository documentation SHALL explain that the `langfuse-postgres` Compose service is ignored for Spring Boot service-connection discovery and that local GraphRAG startup uses its explicit `GRAPHRAG_POSTGRES_*` datasource configuration.

#### Scenario: Local startup guidance is followed
- **WHEN** an operator provisions the empty GraphRAG database and starts the application
- **THEN** the documented commands route GraphRAG to `graphrag / graphrag / app`
- **AND** do not present Langfuse's bootstrap database as a GraphRAG datasource
