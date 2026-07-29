# Polyglot Persistence Cutover

GraphRAG uses PostgreSQL for all operational metadata and Neo4j only for graph-native
artifacts. The local Langfuse deployment shares the PostgreSQL server, but it has a
separate database and role:

| Owner | Database | Role | Schema/data |
|---|---|---|---|
| GraphRAG | `graphrag` | `graphrag` | Operational tables in `app` |
| Langfuse | `langfuse` | `langfuse` | Langfuse-managed objects |
| GraphRAG | Neo4j `neo4j` | `neo4j` | Chunks, embeddings, evidence, facts, provenance |

The final cutover is reset-only. It is not an online migration and does not preserve
existing GraphRAG PostgreSQL or Neo4j data unless backups are taken first.

## Safety Boundary

The following operations are forbidden:

- `docker compose down -v`
- deleting the Compose `langfuse_postgres_data` volume
- dropping, recreating, restoring over, or running GraphRAG Flyway migrations against
  the `langfuse` database
- deleting a broad Docker volume set to reset GraphRAG

Only the `graphrag` PostgreSQL database (or its `app` schema when explicitly using a
schema-only reset) and the named GraphRAG Neo4j volumes may be reset.

## Provision and Start

Start PostgreSQL and Neo4j first:

```bash
docker compose up -d langfuse-postgres neo4j
docker compose exec -T langfuse-postgres \
  bash /docker-entrypoint-initdb.d/20-init-graphrag.sh
```

The provisioning script is idempotent. It creates the unprivileged `graphrag` role,
the `graphrag` database, and the owned `app` schema without reading or modifying
Langfuse tables.

Start the application only after both stores are healthy:

```bash
./mvnw spring-boot:run
```

To run the complete local Langfuse stack:

```bash
docker compose --profile langfuse up -d
```

## Optional GraphRAG Backup

Create the destination directory on the host, then dump only the GraphRAG database:

```bash
mkdir -p backups
docker compose exec -T langfuse-postgres \
  pg_dump --username=graphrag --dbname=graphrag --format=custom \
  > backups/graphrag.dump
```

This command does not dump `langfuse`. Keep a separate, edition-appropriate Neo4j
database backup if rollback must restore existing graph data.

## Reset-Only Cutover

Stop the GraphRAG application. Langfuse may remain running.

Reset only the GraphRAG PostgreSQL database:

```bash
docker compose exec -T langfuse-postgres \
  psql --username=langfuse --dbname=langfuse --set=ON_ERROR_STOP=1 \
  --command="DROP DATABASE IF EXISTS graphrag WITH (FORCE)" \
  --command="CREATE DATABASE graphrag OWNER graphrag"
docker compose exec -T langfuse-postgres \
  bash /docker-entrypoint-initdb.d/20-init-graphrag.sh
```

Reset only the named GraphRAG Neo4j volumes:

```bash
docker compose stop neo4j
docker compose rm -f neo4j
docker volume rm graphrag_neo4j_data graphrag_neo4j_logs
docker volume create graphrag_neo4j_data
docker volume create graphrag_neo4j_logs
docker compose up -d neo4j
```

Restart GraphRAG. Flyway recreates `graphrag.app`, and startup seeders recreate the
default AI profile and bootstrap schemas in PostgreSQL. The graph schema initializer
creates only chunk/evidence constraints and indexes; embedding-space vector indexes
are created when chunks are embedded.

## Restore GraphRAG

Stop GraphRAG and recreate only its database as shown above. Then restore the
database-scoped dump:

```bash
docker compose exec -T langfuse-postgres \
  pg_restore --username=graphrag --dbname=graphrag \
  --clean --if-exists --no-owner --no-privileges \
  < backups/graphrag.dump
```

The dump and restore commands must always name `--dbname=graphrag`. Never restore a
GraphRAG dump while connected to `langfuse`.

## Smoke Verification

Verify database, role, schema, and Flyway placement:

```bash
docker compose exec -T langfuse-postgres \
  psql --username=graphrag --dbname=graphrag \
  --command="SELECT current_database(), current_user, current_schema()" \
  --command="SELECT installed_rank, version, description, success FROM app.flyway_schema_history ORDER BY installed_rank"
```

Expected identity is `graphrag / graphrag / app`. No
`public.flyway_schema_history` table should exist.

Verify the application and relational seeds:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8080/api/v1/ai-profiles
curl --fail http://localhost:8080/api/v1/schemas
```

Verify graph schema and purity:

```bash
docker compose exec -T neo4j cypher-shell -u neo4j -p notverysecret \
  "SHOW INDEXES YIELD name, type RETURN name, type ORDER BY name"
docker compose exec -T neo4j cypher-shell -u neo4j -p notverysecret \
  "MATCH (n) UNWIND labels(n) AS label WITH collect(DISTINCT label) AS labels
   RETURN [label IN labels WHERE label IN
   ['AiProfile','RuntimeSettingOverride','KnowledgeBase','SchemaDefinition',
    'DocumentUpload','DocumentProcessingRun','ExtractionRun','SchemaDraft',
    'SchemaDraftEvaluationRun','SchemaDraftEvaluationOutcome',
    'SchemaDraftPublication','SchemaReprocessingPlan','SchemaReprocessingItem']] AS prohibited"
docker compose exec -T neo4j cypher-shell -u neo4j -p notverysecret \
  "MATCH ()-[r]->() WITH collect(DISTINCT type(r)) AS types
   RETURN [type IN types WHERE type IN
   ['USES_SCHEMA','USES_AI_PROFILE','HAS_CHUNK','HAS_EXTRACTION_RUN',
    'HAS_PROCESSING_RUN','HAS_DRAFT','HAS_PUBLICATION','HAS_REPROCESSING_PLAN']] AS prohibited"
```

Both prohibited lists must be empty. After document processing, Neo4j may contain
`DocumentChunk`, `EmbeddingSpace_*`, schema-defined fact labels and relationships,
`GraphExtractionEvidence`, `NodeExtractionEvidence`,
`RelationshipExtractionEvidence`, `HAS_GRAPH_EVIDENCE`, `ASSERTS_NODE`,
`ASSERTS_FROM`, and `ASSERTS_TO`.

If Langfuse is enabled, verify it independently:

```bash
docker compose ps langfuse-postgres langfuse-web langfuse-worker
curl --fail http://localhost:3000/api/public/health
```

## Rollback

If no backups were taken, rollback can redeploy the previous application but cannot
recover discarded GraphRAG data. When backups exist:

1. Stop GraphRAG.
2. Redeploy the prior application version.
3. Restore only the `graphrag` PostgreSQL dump.
4. Restore only the GraphRAG Neo4j database using the backup procedure selected
   before cutover.
5. Re-run relational, graph-purity, API, and Langfuse health checks.

Langfuse needs no restore because its database and PostgreSQL volume are never part
of the cutover.

## Shared-Instance Monitoring

Monitor both database workloads independently:

```sql
SELECT datname, usename, state, count(*)
FROM pg_stat_activity
WHERE datname IN ('graphrag', 'langfuse')
GROUP BY datname, usename, state
ORDER BY datname, usename, state;

SELECT a.datname, a.usename, l.mode, l.granted, count(*)
FROM pg_locks l
JOIN pg_stat_activity a ON a.pid = l.pid
WHERE a.datname IN ('graphrag', 'langfuse')
GROUP BY a.datname, a.usename, l.mode, l.granted
ORDER BY a.datname, l.granted, l.mode;

SELECT datname, pg_size_pretty(pg_database_size(datname))
FROM pg_database
WHERE datname IN ('graphrag', 'langfuse');

SELECT version, description, execution_time, success
FROM app.flyway_schema_history
ORDER BY installed_rank;
```

Track these application/container signals as well:

- Hikari `hikaricp.connections.acquire` latency and active/idle/pending connections
- JDBC maximum/active connections and datasource health
- PostgreSQL and Langfuse container CPU, memory, restart count, and storage growth
- Neo4j query latency, store size, page-cache pressure, and vector-index state
- Flyway migration duration and failures during each deployment

Alert on sustained pool acquisition latency, pending connections, lock waits,
unexpected database growth, failed migrations, unhealthy Langfuse services, or
prohibited Neo4j labels/relationships.
