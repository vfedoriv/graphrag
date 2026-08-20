# Deployment, persistence safety, and troubleshooting

The checked-in Compose topology is a local development environment, not a production deployment. Production must supply durable storage, secret management, network isolation, authentication/authorization, backups, capacity planning, and SLOs.

## Local persistence provisioning

```bash
docker compose up -d langfuse-postgres neo4j
docker compose exec -T langfuse-postgres \
  bash /docker-entrypoint-initdb.d/20-init-graphrag.sh
```

`langfuse-postgres` is ignored by Spring Boot service-connection discovery. Explicit `GRAPHRAG_POSTGRES_*` values route the backend to the `graphrag` database/role and Flyway `app` schema. Neo4j uses external data/log volumes.

## Safety rules

- Never run `docker compose down -v` for this stack.
- Never delete `langfuse_postgres_data` or drop the `langfuse` database while retaining other Langfuse services.
- Preserve and back up `langfuse_garage_meta` and `langfuse_garage_data` as one consistent pair; regularly test restore.
- Back up PostgreSQL, Neo4j, and the configured GraphRAG document/draft filesystem according to their distinct ownership boundaries.
- Rotate local credentials and never expose default database or object-store ports publicly.

## Deployment profiles

Run the backend with a deployment-selected AI profile (`openai` or `lm_studio`) and optionally `langfuse`. PostgreSQL/Neo4j connection values and credentials remain deployment-managed. Runtime profiles stored in PostgreSQL select provider behavior per knowledge base after startup.

## Production-readiness concerns

- Add authentication/authorization and tenant policy for every `/api/v1/**` route.
- Terminate TLS and isolate PostgreSQL, Neo4j, model, and telemetry networks.
- Move document binaries to durable storage behind `BinaryStorageService` or guarantee filesystem persistence/backup.
- Define retention for documents, chunks, facts/evidence, durable runs, traces, and draft artifacts.
- Size bounded worker queues, database pools, provider deadlines, chunking, and indexes using representative load tests.
- Monitor API availability, processing/run failures, queue saturation, Neo4j query latency, storage mutations, and provider errors.
- Rehearse restore and incident runbooks for each store independently and for cross-store consistency.

## Troubleshooting paths

**Backend will not start**
: Check Java 25, effective Spring profiles, PostgreSQL at port 5433, initializer completion, Neo4j connectivity, and Flyway validation. Use `/actuator/health` once running.

**AI endpoint fails but health is green**
: Check the KB's assigned profile, profile revision/secret, base URL, model names/dimensions/tokenizer, provider network, and timeout. Default profile startup intentionally has no models.

**Profile assignment returns 409**
: Existing chunks use a different provider/model/dimension/resolved tokenizer. Keep the old profile or plan an explicit compatible reprocessing/removal migration.

**Document process returns 409**
: A completed extraction exists. Review it and retry with explicit overwrite, or use a durable reprocessing plan.

**Chunk migration is not ready**
: Read preview blockers and compare active schema/hash, profile/embedding space, and expected chunker revision. Refresh the preview rather than guessing.

**Advanced search submission returns 409**
: Call readiness and resolve its profile, corpus/index, schema/graph, or configuration blockers. No run was created.

**Generated site diagrams do not render**
: Confirm `target/site/js/mermaid-init.js` exists and the browser can reach the pinned Mermaid CDN module. The Markdown fence itself remains readable/renderable on GitHub.

Use RFC 7807 `detail`, optional `errors`/blockers, durable status/diagnostics, and metadata-only logs to correlate failures without exposing content.
