## 1. Domain Mapping

- [x] 1.1 Audit Neo4j domain nodes with assigned application-managed identifiers.
- [x] 1.2 Add Spring Data `@Version` metadata and getters to assigned-id nodes that do not already have version-based state handling.
- [x] 1.3 Use a distinct persistence metadata name for entities that already expose a business `version` field.
- [x] 1.4 Confirm API DTOs and response mappings do not expose new persistence metadata.

## 2. Repository Contracts

- [x] 2.1 Replace primitive custom repository existence-query return types with nullable wrappers or other non-primitive mapped results.
- [x] 2.2 Update service branches that consume repository existence checks to use null-safe truth evaluation.
- [x] 2.3 Replace custom repository `void` write/delete query methods with count-returning query methods where Spring Data metadata noise is triggered.
- [x] 2.4 Confirm service behavior remains unchanged when count-returning write queries are invoked for side effects.

## 3. Verification

- [x] 3.1 Run focused unit tests for document processing, schema registry, AI profile management, and runtime settings service behavior.
- [x] 3.2 Run Neo4j-backed integration tests for document processing persistence paths.
- [x] 3.3 Run Neo4j-backed integration tests for schema, knowledge-base, and AI profile persistence paths.
- [x] 3.4 Inspect relevant integration-test output for absence of `DefaultNeo4jIsNewStrategy` assigned-id warnings and `PropertyDescriptorSource` primitive/void metadata warnings in exercised paths.
