# shared-postgresql-isolation Specification

## Purpose
Define how GraphRAG safely provisions, owns, migrates, and observes its relational state on a PostgreSQL server shared with Langfuse.

## Requirements

### Requirement: GraphRAG uses an isolated database on the shared PostgreSQL server
The system SHALL connect GraphRAG to a dedicated `graphrag` database through a dedicated non-superuser role and an application-owned `app` schema, separate from Langfuse's database, role, schema objects, and migration history.

#### Scenario: GraphRAG starts on the shared server
- **WHEN** GraphRAG starts with valid dedicated datasource credentials
- **THEN** its application tables and Flyway history exist only in `graphrag.app`
- **AND** no GraphRAG table or Flyway record is created in the `langfuse` database

#### Scenario: GraphRAG credentials are least-privileged
- **WHEN** the GraphRAG role attempts an administrator, role-management, replication, or cross-database operation
- **THEN** PostgreSQL rejects the operation

### Requirement: Provisioning preserves an existing Langfuse installation
The system SHALL provide idempotent provisioning for both fresh and initialized PostgreSQL volumes without resetting the shared volume or modifying Langfuse-owned data.

#### Scenario: Provision an initialized shared server
- **WHEN** the operator runs GraphRAG provisioning on a server whose `langfuse` database already contains data
- **THEN** the dedicated GraphRAG role, database, and schema are created or verified
- **AND** the existing Langfuse data remains unchanged

#### Scenario: Provisioning is repeated
- **WHEN** the operator reruns provisioning after GraphRAG resources already exist
- **THEN** provisioning succeeds without replacing credentials, ownership, or Langfuse resources

### Requirement: Relational startup is managed and observable
The system MUST validate the Flyway-managed schema at startup, refuse unmanaged non-empty state, bound its default connection pool, and expose PostgreSQL health and pool usage without exposing credentials.

#### Scenario: Unmanaged schema is non-empty
- **WHEN** GraphRAG starts against a non-empty `app` schema without its Flyway history
- **THEN** startup fails instead of baselining the schema

#### Scenario: Operators inspect datasource status
- **WHEN** Actuator and runtime configuration metadata are queried
- **THEN** PostgreSQL health and pool utilization are available
- **AND** the datasource password remains masked
