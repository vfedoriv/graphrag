## Context

Graph extraction, Cypher generation, and Cypher validation all need the active schema for a knowledge base. Today each service performs its own lookup sequence: load the knowledge base, check `activeSchemaId`, load the schema definition, and parse the schema JSON. This duplicates repository and parser dependencies across workflows that should only need "the active schema context".

## Goals / Non-Goals

**Goals:**
- Introduce a shared active schema resolver for knowledge-base-scoped workflows.
- Preserve current error behavior and API responses.
- Reduce duplicated repository/schema parsing dependencies in query and extraction services.
- Keep the resolver small enough to remain a use-case helper, not a new domain aggregate.

**Non-Goals:**
- Changing schema activation semantics.
- Changing schema JSON validation rules.
- Repackaging the whole project.
- Introducing caching for active schemas.

## Decisions

- Create an `ActiveSchemaResolver` service in the application/service layer.
  - Rationale: the resolver coordinates repositories and parsing, so it belongs above repositories and schema parsing components.
  - Alternative considered: add methods to `SchemaRegistryService`. Rejected because schema registry already owns schema lifecycle; active schema lookup is consumed by query and extraction workflows.
- Return a compact immutable context object.
  - Suggested shape: knowledge base node/id, schema definition node/id, and parsed `SchemaDocument`.
  - Rationale: consumers need both persisted identifiers and parsed schema content.
- Keep exception types and messages behavior-compatible.
  - Rationale: controllers and tests already rely on existing failure semantics.
- Refactor consumers incrementally.
  - `GraphExtractionService`, `CypherGenerationService`, and `CypherValidationService` should each delegate to the resolver instead of repeating lookup logic.

## Risks / Trade-offs

- Resolver becomes another broad service dependency -> Keep it focused only on active schema resolution.
- Hidden behavior change in errors -> Preserve existing exception types and add focused tests for missing knowledge base, missing active schema, and missing schema definition.
- Future desire for caching -> Do not add caching in this change; caching would need separate invalidation semantics around schema activation.
