## 1. Correct Local Datasource Routing

- [x] 1.1 Exclude `langfuse-postgres` from Spring Boot Docker Compose service-connection discovery while preserving Compose lifecycle behavior.
- [x] 1.2 Keep existing datasource, JPA/Flyway schema, and provisioning defaults aligned on `graphrag / graphrag / app`.
- [x] 1.3 Add a focused file-based configuration regression test for the Compose ignore label and aligned defaults.

## 2. Simplify Persistence Guidance

- [x] 2.1 Update `docs/PERSISTENCE_CUTOVER.md` for the one-time destructive fresh-start reset, explicit datasource precedence, provisioning, startup, and simple smoke checks.
- [x] 2.2 Remove backup, restore, quarantine, dependency-inventory, and recurring cutover-verifier requirements that are unnecessary for disposable data.
- [x] 2.3 Synchronize overlapping startup and reset guidance in `README.md`, `AGENTS.md`, and `CLAUDE.md`.
- [x] 2.4 Update documentation regression coverage so the Compose exclusion and fresh-start commands cannot drift.

## 3. Reset the Local Disposable Stores

- [x] 3.1 Stop GraphRAG and affected local services, then remove the known GraphRAG-owned `langfuse.app` residue.
- [x] 3.2 Drop/recreate the disposable dedicated `graphrag` database and reset the named GraphRAG Neo4j volumes.
- [x] 3.3 Run the idempotent PostgreSQL provisioning script and start the empty PostgreSQL and Neo4j stores.
- [x] 3.4 Start GraphRAG and confirm application health plus the effective `graphrag / graphrag / app` identity.

## 4. Final Verification

- [x] 4.1 Run the focused configuration and documentation regression tests.
- [x] 4.2 Run `./mvnw test` and confirm the deterministic suite passes without external AI credentials.
- [x] 4.3 Run `graphify update .` and review the final diff for unrelated or sensitive changes.
