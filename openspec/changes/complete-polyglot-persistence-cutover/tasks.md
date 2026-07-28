## 1. Cutover Readiness

- [ ] 1.1 Verify all seven prerequisite migration changes are complete and no pending storage or workflow mutation requires reconciliation
- [ ] 1.2 Inventory operational Neo4j entities, repositories, relationships, constraints, migrations, initializers, and callers slated for removal
- [ ] 1.3 Add architecture checks that fail when retired operational SDN types, repositories, or unqualified persistence transactions remain

## 2. Legacy Graph Removal

- [ ] 2.1 Remove operational Neo4j models and repositories for profiles, settings, KBs, schemas, documents, runs, draft workflows, publications, and reprocessing
- [ ] 2.2 Remove retired ownership/run relationships, metadata migrations, version backfills, and initializers
- [ ] 2.3 Finalize graph schema initialization for chunks, evidence, facts, provenance, scope, and vector indexes only
- [ ] 2.4 Add allowed-label/relationship graph-purity assertions to the canonical end-to-end flow

## 3. Isolation and Operational Verification

- [ ] 3.1 Build a shared PostgreSQL Testcontainer fixture with separate Langfuse and GraphRAG databases and roles plus Langfuse sentinel data
- [ ] 3.2 Verify provisioning, Flyway, datasource identity, reset, backup, and restore operations never modify Langfuse objects or data
- [ ] 3.3 Verify explicit transaction beans, store-specific rollback, SDN template queries, and cross-store recovery
- [ ] 3.4 Run API, schema, settings, profile, document, extraction, search, draft, publication, reprocessing, graph-purity, and Langfuse health smoke tests

## 4. Operations and Documentation

- [ ] 4.1 Document database-scoped provisioning, reset-only cutover, startup ordering, smoke verification, `pg_dump`, `pg_restore`, rollback, and forbidden shared-volume operations
- [ ] 4.2 Document shared-instance monitoring for connections, acquisition latency, locks, CPU, storage, and migration duration
- [ ] 4.3 Synchronize overlapping persistence facts and commands in `README.md`, `AGENTS.md`, and `CLAUDE.md`

## 5. Final Verification

- [ ] 5.1 Run the canonical end-to-end tests without external AI credentials
- [ ] 5.2 Run `./mvnw test` and inspect the final Neo4j database for prohibited labels and relationships
