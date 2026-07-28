## 1. Shared PostgreSQL Provisioning

- [ ] 1.1 Make the existing `langfuse-postgres` service available whenever GraphRAG runs while preserving the PostgreSQL 17 image, host port, and shared volume
- [ ] 1.2 Add dedicated GraphRAG database, role, password, and schema environment configuration without exposing administrator credentials to the application
- [ ] 1.3 Add an idempotent provisioning script and first-initialization mount for the `graphrag` role, database, and `app` schema
- [ ] 1.4 Test provisioning against both a fresh server and a populated existing Langfuse database, including repeat execution and privilege isolation

## 2. Relational Runtime Foundation

- [ ] 2.1 Add JPA, Flyway, PostgreSQL driver, and PostgreSQL Testcontainers dependencies
- [ ] 2.2 Configure the datasource, `app` schema, Hibernate validation, disabled open-session-in-view, disabled Flyway baselining, and environment-overridable Hikari limits
- [ ] 2.3 Expose PostgreSQL health, pool utilization, and acquisition metrics while masking credentials
- [ ] 2.4 Add startup tests for empty managed schemas and rejection of unmanaged non-empty schemas

## 3. Explicit Persistence Routing

- [ ] 3.1 Separate relational and graph entity/repository packages and bind each repository scanner explicitly
- [ ] 3.2 Define the primary JPA `transactionManager`, the `neo4jTransactionManager`, and the transaction-aware `neo4jTemplate`, applying available Boot customizers
- [ ] 3.3 Add `@RelationalTransactional` and `@GraphTransactional` with read-only and propagation aliases
- [ ] 3.4 Replace foundation-scope ambiguous persistence transactions and add architecture rules prohibiting raw or unqualified transactions, mixed repositories, and transactional self-invocation

## 4. Verification

- [ ] 4.1 Verify relational rollback affects only PostgreSQL and graph rollback affects only Neo4j
- [ ] 4.2 Exercise an SDN custom repository query to verify explicit `Neo4jTemplate` wiring
- [ ] 4.3 Run focused persistence/configuration tests and `./mvnw test`
