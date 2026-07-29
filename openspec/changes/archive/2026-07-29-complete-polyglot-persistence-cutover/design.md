## Context

The preceding changes establish PostgreSQL authority for every operational aggregate and direct scope for every retained graph artifact. The remaining work is intentionally destructive only to disposable GraphRAG stores: eliminate compatibility structures, prove the final topology, and give operators a cutover that cannot affect Langfuse's data.

## Goals / Non-Goals

**Goals:**

- Remove all retired operational labels, relationships, repositories, initializers, and constraints from Neo4j.
- Verify PostgreSQL/Langfuse isolation and Neo4j graph purity.
- Document safe reset, backup, restore, monitoring, startup, and rollback procedures.

**Non-Goals:**

- Preserving existing GraphRAG data.
- Resetting the shared PostgreSQL volume or `langfuse` database.
- Changing public APIs or introducing online dual-write migration.

## Decisions

1. **Gate cutover on all migration changes.** No operational Neo4j structure is removed until static caller checks and integration tests prove its replacement is active.

2. **Reset only GraphRAG-owned databases.** Stop GraphRAG; Langfuse may remain running. Drop/recreate only the `graphrag` database (or its `app` schema according to the documented command) and reset only the GraphRAG Neo4j database/volume. Never remove `langfuse_postgres_data`.

3. **Use explicit smoke invariants.** After startup, verify Flyway history under `graphrag.app`, datasource role identity, default profile/schema seeds, API health, Langfuse health, and absence of retired Neo4j labels and relationships.

4. **Test shared-server isolation realistically.** One PostgreSQL Testcontainer hosts separate `langfuse` and `graphrag` databases and roles. Seed sentinel Langfuse tables/data, run GraphRAG provisioning/Flyway/reset operations, and prove the sentinel remains unchanged.

5. **Back up per database.** Operator guidance uses database-scoped `pg_dump`/`pg_restore` with GraphRAG credentials/ownership rules. Monitoring covers connections, locks, CPU, storage, and migration duration for both applications.

6. **Remove compatibility code rather than leave dormant paths.** Delete operational SDN entities/repositories and metadata migration/backfill initializers. Retain only chunk-oriented SDN persistence and `Neo4jClient` graph adapters.

## Risks / Trade-offs

- [An overlooked caller needs a removed graph structure] → Combine source scans, architecture rules, focused tests, and the canonical full-flow test before removal.
- [Operator resets the shared volume] → Provide explicit guarded commands and warnings naming the forbidden volume/database operations.
- [Graph purity assertion is too narrow] → Enumerate allowed labels/relationships and assert both absence of retired structures and presence of canonical graph artifacts.
- [Rollback after reset cannot recover GraphRAG data] → State reset-only semantics clearly and require a database-scoped backup when recovery is desired.

## Migration Plan

1. Verify all prerequisite changes and reconcile pending external mutations.
2. Remove legacy operational Neo4j code and schema initialization.
3. Add isolation, purity, routing, recovery, and full-flow verification.
4. Update Compose/operator and contributor documentation.
5. Back up if required, stop GraphRAG, provision/reset only GraphRAG stores, and restart dependencies and application.
6. Run smoke checks for APIs, PostgreSQL isolation, graph purity, and Langfuse health.

Rollback redeploys the prior application and restores only database-scoped GraphRAG backups. Langfuse requires no rollback because its database and volume are never changed.

## Open Questions

- None.
