# Prerequisites and local setup

GraphRAG is a Java 25 / Spring Boot 4.1.1 API. PostgreSQL 17 owns operational state, Neo4j 5 owns graph-native data, and document binaries are stored on the local filesystem by default. AI-backed operations need an OpenAI-compatible profile; the default Spring profile can boot without one.

## Prerequisites

- JDK 25 available to the Maven Wrapper.
- Docker with Compose for PostgreSQL and Neo4j.
- `curl` for the examples.
- An OpenAI API key, or a reachable LM Studio instance, for processing, extraction, generation, `/ask`, and advanced search.

Use `./mvnw`, not a separately installed Maven. No Node or Python toolchain is needed for either the application or its documentation.

## Provision required persistence

Start PostgreSQL and Neo4j, then idempotently provision GraphRAG's database, role, and Flyway-managed `app` schema:

```bash
docker compose up -d langfuse-postgres neo4j
docker compose exec -T langfuse-postgres \
  bash /docker-entrypoint-initdb.d/20-init-graphrag.sh
```

The local datasource is `jdbc:postgresql://localhost:5433/graphrag` with the `graphrag` role. Neo4j listens on `bolt://localhost:7687` and its browser on `http://localhost:7474`. These defaults are for local development only.

Do not use `docker compose down -v`: the PostgreSQL volume may also contain Langfuse state. See [deployment and troubleshooting](../operations/deployment-troubleshooting.md) for persistence safety.

## Start the backend

The default profile disables model autoconfiguration. It is useful for non-AI endpoints and startup checks:

```bash
./mvnw spring-boot:run
```

For OpenAI-compatible runtime clients seeded from the OpenAI profile:

```bash
OPENAI_API_KEY=<key> ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=openai
```

For the configured LM Studio endpoint:

```bash
LM_STUDIO_API_KEY=lm-studio ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=lm_studio
```

The database seeds a default AI profile from effective `app.model.*` values only when no default profile exists. Subsequent knowledge bases receive the persisted default profile. Provider behavior is then managed with [AI profile APIs](../workflows/knowledge-bases-profiles.md), not by editing raw provider settings at runtime.

## Verify startup

```bash
curl http://localhost:8080/actuator/health
```

A healthy response confirms the application and configured persistence connections. It does not call an AI provider. Explore the contract at [Swagger UI](http://localhost:8080/swagger-ui/index.html) or fetch [OpenAPI JSON](http://localhost:8080/v3/api-docs).

## Common startup failures

- PostgreSQL connection refused: ensure `langfuse-postgres` is healthy and the GraphRAG initializer completed.
- Neo4j authentication or database failure: verify the Compose service and `spring.neo4j.*` / `app.neo4j.database` deployment settings.
- AI operation fails while health is green: start with `openai` or `lm_studio`, then verify the knowledge base's assigned profile and secret.
- Port already in use: change deployment-managed port mappings and matching connection properties; the runtime settings API cannot reassign active database connections.

Continue with the [first end-to-end run](first-run.md).
