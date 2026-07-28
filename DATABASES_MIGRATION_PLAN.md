# PostgreSQL Migration with Shared-Instance Isolation

## Summary

Adopt strict polyglot persistence:

- PostgreSQL becomes canonical for application metadata and workflow state.
- Neo4j retains only chunks, embeddings, extracted facts, evidence, and graph-native relationships.
- Reuse the existing PostgreSQL 17 instance currently used by Langfuse.
- Isolate GraphRAG in a separate `graphrag` database owned by a dedicated non-superuser `graphrag` role.
- Preserve all `/api/v1` contracts and existing behavior.
- Cut over with an empty GraphRAG database and empty Neo4j database; Langfuse data must remain intact.

The multi-store configuration must incorporate the lessons from `switch-stack-ai`: explicitly create both transaction managers, explicitly bind repository packages to them, and construct `Neo4jTemplate` with its Neo4j transaction manager.

## Shared PostgreSQL Topology

```text
PostgreSQL server: langfuse-postgres
│
├── database: langfuse
│   └── owner/user: langfuse
│       └── managed by Langfuse
│
└── database: graphrag
    └── owner/user: graphrag
        └── schema: app
            └── managed by GraphRAG Flyway
```

Rules:

- GraphRAG must never use the Langfuse database, role, or Flyway history.
- Langfuse must never use the `graphrag` role or `app` schema.
- The GraphRAG role is `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, and `NOREPLICATION`.
- GraphRAG's Flyway history exists only in `graphrag.app.flyway_schema_history`.
- Backups and restores operate per database using `pg_dump`/`pg_restore`.
- Never reset or delete the shared PostgreSQL volume during GraphRAG cutover.

## Target Data Ownership

| PostgreSQL `graphrag` database | Neo4j |
|---|---|
| AI profiles | `DocumentChunk` nodes and embeddings |
| Knowledge bases | Extracted schema-defined nodes |
| Schema definitions and KB associations | Extracted schema-defined relationships |
| Document metadata | `GraphExtractionEvidence` |
| Processing and extraction run history | `ASSERTS_NODE`, `HAS_GRAPH_EVIDENCE`, `MENTIONS` |
| Runtime setting overrides | Vector and graph-artifact indexes |
| Filesystem mutation journals | Graph-scoping identifiers |
| Schema draft, evaluation, publication, and reprocessing workflows | |
| Worker claims, retries, counters, and lifecycle state | |

Remove operational Neo4j labels and relationships, including `KnowledgeBase`, `AiProfile`, `SchemaDefinition`, `DocumentUpload`, `DocumentProcessingRun`, `ExtractionRun`, all `SchemaDraft*`, all `SchemaReprocessing*`, runtime-setting and storage-mutation nodes, `USES_SCHEMA`, `OWNS_DRAFT`, `BELONGS_TO_DRAFT`, `HAS_PROCESSING_RUN`, and `HAS_EXTRACTION_RUN`.

## Implementation Changes

### 1. Capture the Architecture Decision

- Create an OpenSpec change covering relational ownership, graph purity, shared PostgreSQL deployment, explicit transaction routing, reset-only cutover, and unchanged API contracts.
- Update specifications for documents, schemas, draft workflows, settings, hybrid search, graph cleanup, orchestration, and architecture boundaries.

### 2. Provision the Shared PostgreSQL Instance

- Keep the existing `langfuse-postgres` Compose service and PostgreSQL 17 image.
- Make `langfuse-postgres` available without enabling the full `langfuse` profile, because GraphRAG now requires it even when Langfuse tracing is disabled.
- Keep its existing host port `5433`; local GraphRAG uses `jdbc:postgresql://localhost:5433/graphrag`.
- Do not add a second PostgreSQL service or volume.
- Add an idempotent provisioning script that:
  - Connects using the existing PostgreSQL administrator role.
  - Creates the `graphrag` role if absent.
  - Creates the `graphrag` database owned by that role if absent.
  - Creates the `app` schema owned by `graphrag`.
  - Grants the role privileges only inside its database/schema.
- Mount a first-initialization version under `docker-entrypoint-initdb.d`, but also provide an operator-run provisioning command because entrypoint initialization does not run against an existing PostgreSQL volume.
- Keep administrator credentials out of GraphRAG configuration; GraphRAG receives only its dedicated username/password.
- Add environment variables such as `GRAPHRAG_POSTGRES_USER`, `GRAPHRAG_POSTGRES_PASSWORD`, and `GRAPHRAG_POSTGRES_DB`.

### 3. Add JPA and Flyway

- Add `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, the PostgreSQL runtime driver, and PostgreSQL Testcontainers support.
- Configure schema `app`, `spring.jpa.hibernate.ddl-auto=validate`, and `spring.jpa.open-in-view=false`.
- Keep `spring.flyway.baseline-on-migrate=false`; an unmanaged non-empty GraphRAG database must fail startup.
- Configure a conservative Hikari pool default of maximum 10 connections and minimum idle 2, with environment overrides.
- Expose pool usage, acquisition latency, and PostgreSQL health through Actuator so shared-instance resource contention is visible.
- Expose GraphRAG datasource metadata as deployment-managed runtime settings and mask its password.

### 4. Configure Both Persistence Stacks Explicitly

Relational configuration must:

- Use `@EnableJpaRepositories` with a dedicated relational repository package, `entityManagerFactoryRef="entityManagerFactory"`, and `transactionManagerRef="transactionManager"`.
- Define `@Bean("transactionManager") @Primary` returning a `JpaTransactionManager` bound to the auto-configured `EntityManagerFactory`.
- Retain the conventional `transactionManager` name.
- Continue using Boot's datasource, Hibernate, and `EntityManagerFactory` auto-configuration.

Neo4j configuration must:

- Use `@EnableNeo4jRepositories` with a graph-only repository package, `neo4jTemplateRef="neo4jTemplate"`, and `transactionManagerRef="neo4jTransactionManager"`.
- Define `@Bean("neo4jTransactionManager")` using the auto-configured `Driver` and `DatabaseSelectionProvider`.
- Define `@Bean("neo4jTemplate")` with `Neo4jTemplate(neo4jClient, neo4jMappingContext, neo4jTransactionManager)`.
- Continue using Boot's Neo4j driver, client, mapping context, conversions, and database selection.
- Apply available Boot transaction-manager customizers to both explicit managers.

JPA repositories, Neo4j repositories, JPA entities, and Neo4j nodes must be in disjoint packages.

### 5. Enforce Transaction-Manager Selection

- Add composed annotations `@RelationalTransactional` and `@GraphTransactional`, hard-wired to their respective managers and exposing `readOnly` and `propagation`.
- Replace every persistence-related unqualified `@Transactional`.
- PostgreSQL services and `REQUIRES_NEW` lifecycle checkpoints use `@RelationalTransactional`.
- Graph adapters use `@GraphTransactional`.
- Cross-store orchestration methods remain unannotated and call separate proxied collaborators.
- Add an architecture rule rejecting raw or unqualified `@Transactional` in persistence-aware classes.
- Do not copy the latent `switch-stack-ai` pattern where a class-level Neo4j qualifier is overridden by unqualified method-level annotations.
- Avoid transactional self-invocation.

Do not introduce XA, a chained transaction manager, or an implied transaction spanning PostgreSQL and Neo4j.

### 6. Create the Relational Model

Create Flyway-managed tables in `graphrag.app`:

- Core: `ai_profile`, `schema_definition`, `knowledge_base`, `knowledge_base_schema`.
- Documents: `document_upload`, `document_processing_run`, `extraction_run`, `document_storage_mutation`.
- Draft workflow: `schema_draft`, `schema_draft_source`, `schema_draft_source_revision`, `schema_draft_analysis_run`, `schema_draft_source_result`, `schema_draft_aggregate_revision`, `schema_draft_conflict`, `schema_draft_decision`, `schema_draft_evaluation_run`, `schema_draft_evaluation_outcome`, `schema_draft_publication`, `schema_draft_storage_mutation`.
- Reprocessing: `schema_reprocessing_plan`, `schema_reprocessing_item`.
- Settings: `runtime_setting_override`.

Use assigned string IDs, `timestamptz`, enum strings with database checks, and JPA optimistic `@Version` columns.

Enforce:

- Unique `(schema name, version)`.
- Unique `(knowledge base, document SHA-256)`.
- One default AI profile through a partial unique index.
- One active completed processing run per document.
- Unique draft revisions, decision sequences, and publication identities.
- Foreign keys with cascade for workflow children and restrict for active schema/profile references.
- Mutation journals without cascading target foreign keys.
- Worker indexes on status, claims, ownership, retries, reuse keys, and creation time.

Replace `SchemaDraftAnalysisLease` with an atomic conditional PostgreSQL update of `schema_draft.running_analysis_run_id`.

Keep existing JSON payloads as text to preserve hashes and fingerprints. JSONB conversion remains outside scope.

### 7. Migrate Operational Services

Migrate in dependency order:

1. Runtime settings and AI profiles.
2. Knowledge bases, schema registry, bootstrap, activation, and profile assignment.
3. Documents, processing runs, extraction runs, and storage reconciliation.
4. Draft analysis, review, evaluation, publication, and reprocessing.

Replace operational `*Node` classes with domain models and JPA persistence adapters. Keep `DocumentChunkNode` Neo4j-specific.

Replace Cypher ownership operations with foreign keys, join-table operations, conditional SQL updates, and JPA transactions. Preserve API mapping, pagination, sorting, validation, optimistic conflict handling, and write-only API keys.

Retire metadata-related Neo4j initializers only after no callers depend on them.

### 8. Make Neo4j Graph-Only

- Add `knowledgeBaseId` to every `DocumentChunk`.
- Add `knowledgeBaseId` to every `GraphExtractionEvidence`.
- Pass `knowledgeBaseId` explicitly through chunk persistence and graph extraction.
- Create evidence without matching an `ExtractionRun` node.
- Keep chunk-to-evidence and evidence-to-fact relationships; remove document/run anchors.
- Filter hybrid search and embedding compatibility directly on `chunk.knowledgeBaseId`.
- Batch-load document metadata from PostgreSQL after Neo4j returns vector hits.
- Resolve failed or stale run IDs from PostgreSQL and clean evidence by `extractionRunId`.
- Clean document artifacts through `DocumentChunk.documentId` and `GraphExtractionEvidence.sourceDocumentId`.
- Delete canonical graph facts only when no evidence remains.
- Retain `Neo4jClient` for dynamic Cypher, projections, vector search, graph writes, and cleanup.
- Restrict SDN repositories to straightforward chunk persistence.
- Replace legacy constraints with chunk, evidence, scope, provenance, and vector indexes.

## Cross-Store Workflow Semantics

Every cross-store operation follows:

1. Commit PostgreSQL intent or run state.
2. Execute an idempotent Neo4j or filesystem operation.
3. Commit PostgreSQL completion state.
4. Retain a retryable PostgreSQL record if a later step fails.

Processing and extraction runs become `RUNNING` before graph work and `COMPLETED` afterward. Interrupted work is recovered as failed/retryable, and partial evidence is removed by run ID. Document replacement/deletion continues using durable mutation journals.

## Test Plan

### Shared PostgreSQL Isolation

- Start one PostgreSQL Testcontainer and create both `langfuse` and `graphrag` databases with separate roles.
- Run GraphRAG Flyway only against `graphrag`.
- Assert GraphRAG creates no tables or Flyway history in `langfuse`.
- Assert GraphRAG's datasource connects as the `graphrag` role.
- Test the provisioning script against both a fresh server and an existing server where the Langfuse database already contains data.
- Verify a GraphRAG database reset leaves Langfuse tables unchanged.

### Transaction Routing

- Assert `transactionManager` is the primary `JpaTransactionManager`.
- Assert `neo4jTransactionManager` is a `Neo4jTransactionManager`.
- Exercise an SDN custom `@Query` to catch the historical `Neo4jTemplate` NPE.
- Verify relational rollback affects only PostgreSQL.
- Verify graph rollback affects only Neo4j.
- Add an architecture test for repository-package separation and qualified transactions.

### Functional Behavior

- Verify Flyway and Hibernate validation from an empty GraphRAG database.
- Test constraints, optimistic locking, pagination, conditional claims, recovery, and rollback.
- Verify hybrid-search knowledge-base isolation and batched document enrichment.
- Verify extraction success, failure, retry, overwrite, and cleanup.
- Verify document replacement/deletion and filesystem reconciliation.
- Verify schema activation, profile compatibility, settings atomicity, draft lifecycle, evaluation, publication, and reprocessing.
- Assert that no retired operational label or relationship exists in Neo4j after the canonical end-to-end flow.
- Run `./mvnw test` and the canonical end-to-end tests without external AI credentials.

## Cutover and Operations

- Provision the `graphrag` role/database inside the existing PostgreSQL instance.
- Stop GraphRAG, but Langfuse may remain running.
- Reset only the `graphrag` database and Neo4j database/volume.
- Never reset `langfuse_postgres_data` or drop the `langfuse` database.
- Start the shared PostgreSQL service and Neo4j, then start GraphRAG.
- Allow GraphRAG Flyway and graph index initialization to complete.
- Seed the default AI profile and bootstrap schemas.
- Execute API, PostgreSQL isolation, graph-purity, and Langfuse health smoke tests.
- Document independent `pg_dump`/`pg_restore` procedures for `graphrag`.
- Monitor PostgreSQL connections, CPU, storage, locks, and migration duration for both applications.
- Synchronize `README.md`, `AGENTS.md`, and `CLAUDE.md`.
- Run `graphify update .` after implementation.

## Assumptions

- GraphRAG and Langfuse share a PostgreSQL server and volume but not a database, role, schema, Flyway history, or application connection pool.
- Existing GraphRAG data may be discarded; Langfuse data must be preserved.
- PostgreSQL is authoritative for identifiers copied into Neo4j.
- Complex graph reads remain `Neo4jClient`-based.
- Existing query-tenancy semantics and JSONB optimization remain outside scope.
- The unrelated uncommitted `application-openai.properties` change must be preserved.
