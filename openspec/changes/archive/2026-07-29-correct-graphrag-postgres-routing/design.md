## Context

GraphRAG and Langfuse share one local PostgreSQL 17 server but are intended to use separate databases and roles. `application.properties` explicitly defaults GraphRAG to `jdbc:postgresql://localhost:5433/graphrag` as role `graphrag`, while the Compose PostgreSQL service is bootstrapped for `langfuse / langfuse`.

Spring Boot Docker Compose service-connection discovery currently recognizes that service as PostgreSQL and replaces GraphRAG's explicit datasource with the Langfuse connection. This caused GraphRAG Flyway objects to be created in `langfuse.app`.

The environment is pre-production and its GraphRAG data is disposable. This correction therefore assumes a fresh start rather than a migration or preservation workflow.

## Goals / Non-Goals

**Goals:**

- Keep Spring Boot Compose lifecycle support for local services.
- Prevent the Langfuse PostgreSQL service from contributing connection details to GraphRAG.
- Preserve the existing explicit `GRAPHRAG_POSTGRES_*` datasource and provisioning path.
- Remove old GraphRAG-owned PostgreSQL and Neo4j state once, then start with empty stores.
- Add a small regression test that prevents configuration drift.

**Non-Goals:**

- Preserving or migrating existing GraphRAG data.
- Adding runtime datasource identity validation or a custom Flyway migration strategy.
- Adding recurring migration, residue, or cutover verification to application startup or the test suite.
- Building backup, restore, quarantine, dependency-inventory, or operator-approval workflows.
- Changing REST APIs, JPA entities, Flyway's application data model, or Langfuse-owned tables.

## Decisions

### Ignore the shared PostgreSQL service for Spring Boot connection discovery

Add `org.springframework.boot.ignore: true` to `langfuse-postgres`. Spring Boot can continue managing the Compose lifecycle, but it will not derive application JDBC connection details from Langfuse's container metadata. GraphRAG will therefore use its explicit `spring.datasource.*` properties backed by `GRAPHRAG_POSTGRES_*`.

Disabling Compose support globally is unnecessary because local lifecycle management remains useful. Changing Langfuse's `POSTGRES_DB` or `POSTGRES_USER` would conflate ownership and is not acceptable.

### Keep deployment configuration simple

Retain the existing GraphRAG defaults:

- database: `graphrag`
- role: `graphrag`
- schema: `app`

Do not add duplicate expected-identity properties. The datasource URL, username, JPA schema, Flyway schema, and provisioning script remain the configuration sources used for a fresh local environment.

### Reset disposable GraphRAG state once

Stop the application and local services. Drop the accidentally created GraphRAG-owned `app` schema from the local `langfuse` database, drop/recreate the dedicated `graphrag` database if present, and recreate the named GraphRAG Neo4j volumes. No backup or dependency inventory is required because the user has explicitly declared the old GraphRAG data disposable and the reset is performed before further use.

The cleanup targets GraphRAG-owned state only; it does not delete the shared PostgreSQL volume or Langfuse-owned schemas/tables.

### Verify configuration rather than every startup

Add a lightweight repository test that reads `compose.yaml`, `application.properties`, and the provisioning script. It asserts the ignore label and aligned `graphrag / graphrag / app` defaults. Existing integration and full-suite tests remain the normal validation path; no special cutover verifier or permanent startup guard is added.

## Risks / Trade-offs

- [The reset destroys all existing GraphRAG state] → This is explicitly accepted for the current pre-production environment.
- [The wrong schema is manually targeted during cleanup] → Use the exact local `langfuse.app` and GraphRAG database/volume names documented by the repository.
- [A future configuration edit reintroduces datasource drift] → Keep the focused configuration regression test.
- [A later deployment needs data preservation] → Design that migration separately when preservation becomes a real requirement; it is not part of this correction.

## Migration Plan

1. Add the Compose ignore label and focused regression test.
2. Stop GraphRAG and the affected local services.
3. Destructively remove the disposable GraphRAG-owned PostgreSQL residue and reset the dedicated GraphRAG PostgreSQL/Neo4j stores.
4. Run the idempotent PostgreSQL provisioning script.
5. Start GraphRAG with its explicit datasource and run the normal test/smoke checks.

Rollback is a code/configuration rollback only. Deleted GraphRAG data is intentionally unrecoverable because no preservation is required.

## Open Questions

None.
