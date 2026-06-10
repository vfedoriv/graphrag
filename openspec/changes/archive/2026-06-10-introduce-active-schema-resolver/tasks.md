## 1. Resolver Contract

- [x] 1.1 Add an immutable active schema context type containing knowledge base identifier, schema definition metadata/identifier, and parsed `SchemaDocument`
- [x] 1.2 Implement `ActiveSchemaResolver` using `KnowledgeBaseRepository`, `SchemaDefinitionRepository`, and `SchemaParser`
- [x] 1.3 Preserve existing exception behavior for unknown knowledge base, missing active schema, and missing schema definition

## 2. Consumer Refactor

- [x] 2.1 Update `GraphExtractionService` to resolve active schema through `ActiveSchemaResolver`
- [x] 2.2 Update `CypherGenerationService` to resolve active schema through `ActiveSchemaResolver`
- [x] 2.3 Update `CypherValidationService` to resolve active schema through `ActiveSchemaResolver`
- [x] 2.4 Remove duplicated repository lookup and schema parsing logic from the refactored consumers where no longer needed

## 3. Verification

- [x] 3.1 Add unit tests for successful active schema resolution and each failure mode
- [x] 3.2 Update affected service tests to mock the resolver instead of duplicated repository/parser dependencies where appropriate
- [x] 3.3 Run `./mvnw test`
