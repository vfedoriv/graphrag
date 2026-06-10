## Why

Active schema lookup is repeated across graph extraction, Cypher generation, and Cypher validation. Each flow independently loads the knowledge base, checks `activeSchemaId`, loads the schema definition, and parses schema content, which increases coupling and makes future behavior drift likely.

## What Changes

- Add a shared active schema resolution use-case component for knowledge-base-scoped workflows.
- Replace duplicated active-schema lookup logic in graph extraction, query generation, and query validation with the shared resolver.
- Preserve current API behavior and error semantics for missing knowledge bases, missing active schemas, and missing schema definitions.
- Keep schema parsing and validation contracts unchanged.

## Capabilities

### New Capabilities
- `active-schema-resolution`: Defines consistent active schema resolution for workflows that need a knowledge base's active schema.

### Modified Capabilities

## Impact

- Affected services: `GraphExtractionService`, `CypherGenerationService`, `CypherValidationService`.
- Affected dependencies: `KnowledgeBaseRepository`, `SchemaDefinitionRepository`, `SchemaParser`.
- No API contract changes are intended.
- Tests should cover resolver behavior and verify existing extraction/query flows still use the active schema consistently.
