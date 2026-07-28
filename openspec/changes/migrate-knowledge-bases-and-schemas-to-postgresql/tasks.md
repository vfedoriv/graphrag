## 1. Prerequisites and Relational Model

- [ ] 1.1 Verify the relational foundation and PostgreSQL AI-profile migration are complete
- [ ] 1.2 Add Flyway migrations for `knowledge_base`, `schema_definition`, and `knowledge_base_schema` with assigned IDs, text payloads, foreign keys, uniqueness, activation support, and optimistic versions
- [ ] 1.3 Add repository tests for immutable schema identity, ownership constraints, concurrent activation, and profile foreign keys

## 2. Knowledge-Base Migration

- [ ] 2.1 Add relational KB entities, repositories, adapters, and API/domain mappers
- [ ] 2.2 Migrate knowledge-base CRUD, default-profile association, pagination, sorting, and lifecycle behavior to relational transactions
- [ ] 2.3 Migrate profile assignment with graph-backed embedding compatibility checks and an atomic conditional relational update
- [ ] 2.4 Verify incompatible assignment leaves the previous profile unchanged under normal and concurrent requests

## 3. Schema Registry Migration

- [ ] 3.1 Add relational schema and KB-association entities, repositories, projections, and adapters
- [ ] 3.2 Migrate schema create/get/list/update/delete validation and inactive-only mutation guards
- [ ] 3.3 Migrate KB association, activation, and active-schema resolution with a single relational activation transaction
- [ ] 3.4 Migrate bootstrap schema seeding idempotently and preserve stored schema text, fingerprints, and API content

## 4. Cleanup and Verification

- [ ] 4.1 Remove runtime callers of operational KB/schema Neo4j repositories while retaining graph-facing ID contracts needed by later changes
- [ ] 4.2 Update integration tests to assert PostgreSQL ownership without reducing extraction/query graph coverage
- [ ] 4.3 Run focused KB/schema/profile-assignment tests and `./mvnw test`
