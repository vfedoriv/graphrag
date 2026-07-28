## Why

GraphRAG currently stores both operational state and graph-native data in Neo4j, which couples workflow consistency, graph traversal, and deployment lifecycle to one database. The migration needs a safe foundation for PostgreSQL-owned application state while preserving Neo4j for graph data and avoiding ambiguous transaction routing.

## What Changes

- Reuse the existing PostgreSQL 17 server while provisioning a dedicated `graphrag` database, non-superuser role, and owned `app` schema without changing the Langfuse database.
- Add the PostgreSQL driver, JPA, Flyway, and PostgreSQL Testcontainers support with schema validation, conservative connection-pool defaults, health reporting, and masked datasource metadata.
- Configure JPA and Neo4j repository packages, transaction managers, and `Neo4jTemplate` explicitly.
- Introduce qualified relational and graph transaction annotations and prohibit ambiguous persistence transactions, cross-store transactions, XA, and chained transaction managers.
- Establish reset-only GraphRAG cutover assumptions while preserving `/api/v1` contracts and Langfuse data.
- This change is a prerequisite for every later persistence-migration change and does not migrate an application domain by itself.

## Capabilities

### New Capabilities

- `shared-postgresql-isolation`: Provisioning, privilege, schema, Flyway-history, observability, and Langfuse-isolation requirements for the shared PostgreSQL server.
- `persistence-transaction-routing`: Explicit repository ownership and transaction-manager selection across PostgreSQL and Neo4j.

### Modified Capabilities

- `architecture-boundary-governance`: Establish PostgreSQL as the operational-state boundary and Neo4j as the graph-data boundary without distributed transactions.

## Impact

- Affects `pom.xml`, application configuration, Compose topology, provisioning scripts, persistence package layout, transaction annotations, Actuator exposure, architecture tests, and PostgreSQL-backed test infrastructure.
- Adds PostgreSQL as a required GraphRAG runtime dependency while retaining the existing Neo4j dependency.
- Does not change public API shapes or migrate existing GraphRAG data.
