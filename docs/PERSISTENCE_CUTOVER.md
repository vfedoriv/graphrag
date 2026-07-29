# GraphRAG Persistence Fresh Start

GraphRAG uses PostgreSQL for operational state and Neo4j only for graph-native
artifacts. The local Langfuse deployment shares the PostgreSQL server, but each
application has its own database and role:

| Owner | Database | Role | Schema/data |
|---|---|---|---|
| GraphRAG | `graphrag` | `graphrag` | Operational tables in `app` |
| Langfuse | `langfuse` | `langfuse` | Langfuse-managed objects |
| GraphRAG | Neo4j `neo4j` | `neo4j` | Chunks, embeddings, evidence, facts, provenance |

The `langfuse-postgres` service has the Compose label
`org.springframework.boot.ignore: true`. Spring Boot can still manage the Compose
lifecycle, but it does not derive GraphRAG JDBC connection details from Langfuse's
container metadata. Local GraphRAG startup therefore uses the explicit
`GRAPHRAG_POSTGRES_*` settings from `application.properties`, whose defaults resolve
to database `graphrag`, role `graphrag`, and schema `app`.

## Safety Boundary

This is a one-time destructive fresh start. Existing GraphRAG PostgreSQL and Neo4j
data is intentionally discarded without backup, restore, quarantine, or migration.

Never run `docker compose down -v`, delete the `langfuse_postgres_data` volume, or
drop the `langfuse` database. The cleanup below targets only:

- the known GraphRAG-owned `app` schema residue inside `langfuse`
- the dedicated `graphrag` database
- the named `graphrag_neo4j_data` and `graphrag_neo4j_logs` volumes

## One-Time Reset

Stop the GraphRAG application first. Then stop services that can write GraphRAG or
Langfuse state while cleanup runs, and make sure PostgreSQL is available:

```bash
docker compose --profile langfuse stop langfuse-web langfuse-worker neo4j
docker compose up -d langfuse-postgres
```

Remove only the accidentally created GraphRAG schema from the Langfuse database,
then drop the disposable dedicated GraphRAG database:

```bash
docker compose exec -T langfuse-postgres \
  psql --username=langfuse --dbname=langfuse --set=ON_ERROR_STOP=1 \
  --command="DROP SCHEMA IF EXISTS app CASCADE"
docker compose exec -T langfuse-postgres \
  psql --username=langfuse --dbname=langfuse --set=ON_ERROR_STOP=1 \
  --command="DROP DATABASE IF EXISTS graphrag WITH (FORCE)"
```

Reset only the named GraphRAG Neo4j volumes:

```bash
docker compose rm -f neo4j
docker volume rm graphrag_neo4j_data graphrag_neo4j_logs
docker volume create graphrag_neo4j_data
docker volume create graphrag_neo4j_logs
```

## Provision and Start

Run the idempotent provisioning script and start the empty Neo4j store:

```bash
docker compose exec -T langfuse-postgres \
  bash /docker-entrypoint-initdb.d/20-init-graphrag.sh
docker compose up -d neo4j
```

The script creates or aligns the unprivileged `graphrag` role, the `graphrag`
database, and its owned `app` schema without modifying Langfuse-owned tables.

Start GraphRAG:

```bash
./mvnw spring-boot:run
```

Flyway creates the application tables in `graphrag.app`; startup seeders recreate
the default AI profile and bootstrap schemas. To start the optional Langfuse stack
again, run:

```bash
docker compose --profile langfuse up -d
```

## Smoke Verification

Confirm the effective PostgreSQL identity and Flyway placement:

```bash
docker compose exec -T langfuse-postgres \
  psql --username=graphrag --dbname=graphrag \
  --command="SELECT current_database(), current_user, current_schema()" \
  --command="SELECT installed_rank, version, description, success FROM app.flyway_schema_history ORDER BY installed_rank"
```

The identity row must report `graphrag / graphrag / app`. Then verify application
health:

```bash
curl --fail http://localhost:8080/actuator/health
```

Finally, run the normal deterministic test suite without external AI credentials:

```bash
./mvnw test
```

No recurring cutover verifier is required. Future environments that need data
preservation require a separately designed migration and recovery procedure.
