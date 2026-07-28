## 1. Prerequisites and Relational Model

- [x] 1.1 Verify the relational foundation and PostgreSQL AI-profile migration are complete
- [x] 1.2 Add Flyway migrations for `knowledge_base`, `schema_definition`, and `knowledge_base_schema` with assigned IDs, text payloads, foreign keys, uniqueness, activation support, and optimistic versions
- [x] 1.3 Add repository tests for immutable schema identity, ownership constraints, concurrent activation, and profile foreign keys

## 2. Knowledge-Base Migration

- [x] 2.1 Add relational KB entities, repositories, adapters, and API/domain mappers
- [x] 2.2 Migrate knowledge-base CRUD, default-profile association, pagination, sorting, and lifecycle behavior to relational transactions
- [x] 2.3 Migrate profile assignment with graph-backed embedding compatibility checks and an atomic conditional relational update
- [x] 2.4 Verify incompatible assignment leaves the previous profile unchanged under normal and concurrent requests

## 3. Schema Registry Migration

- [x] 3.1 Add relational schema and KB-association entities, repositories, projections, and adapters
- [x] 3.2 Migrate schema create/get/list/update/delete validation and inactive-only mutation guards
- [x] 3.3 Migrate KB association, activation, and active-schema resolution with a single relational activation transaction
- [x] 3.4 Migrate bootstrap schema seeding idempotently and preserve stored schema text, fingerprints, and API content

## 4. Cleanup and Verification

- [x] 4.1 Remove runtime callers of operational KB/schema Neo4j repositories while retaining graph-facing ID contracts needed by later changes
- [x] 4.2 Update integration tests to assert PostgreSQL ownership without reducing extraction/query graph coverage
- [x] 4.3 Run focused KB/schema/profile-assignment tests and `./mvnw test`
