## 1. Prerequisite and Schema

- [ ] 1.1 Verify `establish-relational-persistence-foundation` is complete
- [ ] 1.2 Add Flyway migrations for `runtime_setting_override` and `ai_profile` with assigned IDs, timestamps, enum checks, optimistic versions, and the partial default-profile uniqueness index
- [ ] 1.3 Add migration tests for constraints, schema validation, and concurrent default selection

## 2. Runtime Settings Migration

- [ ] 2.1 Add relational setting-override entity, repository, and persistence adapter mappings
- [ ] 2.2 Migrate `RuntimeSettingsService` reads, validated writes, lifecycle reconciliation, and live logging application to relational transactions
- [ ] 2.3 Add deployment-managed PostgreSQL datasource/pool catalog entries and keep datasource passwords sensitive, masked, and non-mutable
- [ ] 2.4 Replace settings persistence tests with PostgreSQL-backed atomicity, optimistic-conflict, lifecycle, and masking coverage

## 3. AI Profile Migration

- [ ] 3.1 Add relational AI-profile entity, repository, and persistence adapter mappings without exposing API keys through entities or DTOs
- [ ] 3.2 Migrate profile CRUD, default promotion/demotion, validation, and exception mapping to relational transactions
- [ ] 3.3 Migrate default profile seeding to an idempotent database-constrained startup flow
- [ ] 3.4 Preserve service interfaces needed by the later knowledge-base profile-assignment migration

## 4. Cleanup and Verification

- [ ] 4.1 Remove runtime callers of settings and profile Neo4j repositories while deferring final legacy label cleanup
- [ ] 4.2 Run focused settings/profile integration tests and `./mvnw test`
