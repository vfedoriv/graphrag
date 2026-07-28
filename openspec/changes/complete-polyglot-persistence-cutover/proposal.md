## Why

Once every operational domain uses PostgreSQL and every graph artifact is directly scoped, temporary Neo4j compatibility structures become a correctness and operational risk. A final cutover change is needed to remove them, prove database isolation and graph purity, and document a reset procedure that cannot damage Langfuse.

## What Changes

- Require all seven preceding migration changes.
- Remove retired operational Neo4j models, repositories, relationships, constraints, migrations, and initializers after verifying that no callers remain.
- Assert that Neo4j contains only chunks, embeddings, extracted facts, evidence, provenance, and graph-native relationships after the canonical end-to-end flow.
- Provide and test reset-only GraphRAG cutover, independent `pg_dump`/`pg_restore`, shared-instance monitoring, and Langfuse-preservation procedures.
- Exercise PostgreSQL role/database/schema/Flyway isolation on fresh and pre-existing shared servers.
- Run full API, transaction-routing, graph-purity, recovery, and end-to-end smoke coverage without external AI credentials.
- Synchronize `README.md`, `AGENTS.md`, and `CLAUDE.md` with the final architecture and operating commands.

## Capabilities

### New Capabilities

- `polyglot-persistence-cutover`: Define safe reset, startup, verification, backup, restore, monitoring, and rollback procedures for the final topology.

### Modified Capabilities

- `architecture-boundary-governance`: Prohibit retired operational persistence structures in Neo4j after cutover.
- `test-coverage-governance`: Require shared-PostgreSQL isolation, transaction routing, graph-purity, and cross-store recovery coverage.
- `documentation-alignment`: Keep contributor and operator guidance synchronized with the required PostgreSQL and Neo4j topology.

## Impact

- Affects legacy Neo4j domain and repository removal, database initialization, Compose operations, integration-test topology, smoke tests, backup/restore guidance, monitoring guidance, and contributor documentation.
- The cutover intentionally discards existing GraphRAG PostgreSQL and Neo4j data while preserving the shared Langfuse database and PostgreSQL volume.
