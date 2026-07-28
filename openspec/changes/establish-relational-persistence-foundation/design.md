## Context

GraphRAG currently starts with Neo4j as its only application database. The target topology reuses the PostgreSQL 17 server and volume already operated for Langfuse, but GraphRAG must have a different database, role, schema, Flyway history, and connection pool. Adding JPA beside Spring Data Neo4j also makes Boot's default repository scanning and transaction-manager selection ambiguous unless both stacks are configured explicitly.

This change creates only the shared platform. Domain tables and adapters are introduced by later changes so each migration remains reviewable.

## Goals / Non-Goals

**Goals:**

- Provision an isolated GraphRAG database safely on fresh and existing shared PostgreSQL servers.
- Establish repeatable Flyway, JPA, datasource, health, and metrics configuration.
- Make relational and graph repository packages and transaction boundaries unambiguous.
- Preserve current HTTP contracts and Neo4j behavior until domain migrations occur.

**Non-Goals:**

- Migrating application aggregates or existing GraphRAG data.
- Changing Langfuse's database, role, schema, migrations, or application services.
- Introducing XA, chained transactions, or automatic compensation across stores.
- Converting existing text JSON payloads to JSONB.

## Decisions

1. **Share the server, not a database or role.** `langfuse-postgres` remains the only PostgreSQL service and keeps its volume and port. An idempotent operator script connects with the existing administrator role and creates the `graphrag` login, database, and `app` schema with least privilege. A first-initialization copy is mounted under `docker-entrypoint-initdb.d`, but operators must be able to provision an already initialized volume.

2. **Use Boot datasource and JPA auto-configuration.** Configure `spring.datasource.*`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, and Flyway's `app` default schema. Keep `baseline-on-migrate=false` so an unmanaged non-empty database fails instead of being silently adopted.

3. **Separate persistence packages.** JPA entities and repositories live in relational-only packages; Neo4j nodes and repositories live in graph-only packages. `@EnableJpaRepositories` references `entityManagerFactory` and `transactionManager`; `@EnableNeo4jRepositories` references `neo4jTemplate` and `neo4jTransactionManager`.

4. **Construct both transaction stacks explicitly.** The primary bean named `transactionManager` is a `JpaTransactionManager`. `neo4jTransactionManager` uses the configured driver and database selection. `neo4jTemplate` is constructed with the Neo4j client, mapping context, and Neo4j transaction manager. Available Boot customizers are applied to both managers.

5. **Encode transaction choice in composed annotations.** `@RelationalTransactional` and `@GraphTransactional` expose read-only and propagation attributes but hard-wire the manager. Persistence-aware code cannot use raw or unqualified `@Transactional`. Cross-store orchestrators remain unannotated and invoke separate proxied collaborators so each checkpoint commits independently.

6. **Use conservative shared-instance defaults.** Hikari defaults to maximum 10 and minimum idle 2 with environment overrides. Actuator exposes datasource health and pool utilization/acquisition metrics. GraphRAG configuration contains only its dedicated credentials; administrator credentials are limited to provisioning.

## Risks / Trade-offs

- [An existing volume skips entrypoint initialization] → Provide and test an idempotent operator-run provisioning command.
- [Repository scanning binds a repository to the wrong store] → Use disjoint packages, explicit enablement, and architecture tests.
- [An unqualified method-level transaction overrides class intent] → Ban raw persistence transactions and test composed annotations and proxy boundaries.
- [Shared PostgreSQL resource contention affects Langfuse] → Bound GraphRAG's pool and expose health, acquisition latency, and utilization.
- [Flyway adopts unmanaged state] → Keep baseline disabled and verify failure against a non-empty unmanaged schema.

## Migration Plan

1. Add dependencies and shared PostgreSQL Compose availability.
2. Add provisioning scripts and verify fresh and pre-existing server behavior.
3. Configure datasource, Flyway, JPA, explicit persistence stacks, and composed annotations.
4. Add isolation, bean-routing, rollback, repository-package, and health tests.
5. Leave application domains on Neo4j until their ordered migration changes.

Rollback removes GraphRAG's datasource configuration and dedicated empty database only; it must not remove the PostgreSQL volume or modify the `langfuse` database.

## Open Questions

- None. Connection limits remain environment-overridable so deployment-specific tuning does not block the architecture.
