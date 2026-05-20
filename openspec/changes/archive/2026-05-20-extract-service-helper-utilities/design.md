## Context

The current service layer contains several classes where public orchestration methods sit beside many private support methods. The largest examples are `GraphExtractionValidationService`, `CypherValidationService`, `GraphWriteService`, `GraphExtractionService`, and `LangChain4jSchemaGenerationService`. These helpers perform deterministic parsing, filtering, identity construction, property allow-listing, type normalization, and result conversion, but their private visibility makes them testable only through broader service scenarios.

The refactor should improve maintainability without changing runtime behavior. Services must continue to own dependencies, transaction boundaries, repository/client calls, Neo4j execution, Spring wiring, and high-level logging. Extracted helpers should own pure or mostly pure transformations that can be covered by focused unit tests.

## Goals / Non-Goals

**Goals:**

- Reduce private utility/helper method density in service classes while keeping service orchestration readable.
- Extract cohesive helper classes with names that describe their domain responsibility, not generic dumping grounds.
- Make deterministic helper behavior directly unit-testable.
- Preserve existing public APIs, persistence semantics, validation results, exception behavior, and logging observability.
- Keep helper classes colocated with the package whose domain they support unless there is a clear cross-package reuse case.

**Non-Goals:**

- Redesign graph extraction, schema generation, query validation, or graph persistence behavior.
- Introduce new REST endpoints, DTOs, configuration properties, database labels, relationship types, or runtime dependencies.
- Move repository/client interaction into static utilities.
- Convert all private methods mechanically; private methods that are trivial, logging-only, or tightly coupled to orchestration can remain private.

## Decisions

1. Extract cohesive support classes by responsibility.

   Use domain-specific classes such as `ExtractionValidationUtils`, `ExtractionFilteringUtils`, `CypherReferenceParser`, `CypherSchemaReferenceValidator`, `GraphIdentityUtils`, `GraphPropertyFilteringUtils`, or similarly precise names chosen during implementation. The exact class names may vary, but each helper must have a narrow purpose and tests that reflect that purpose.

   Alternative considered: one broad `ServiceUtils` class. This is rejected because it would preserve the same cohesion problem under a different name.

2. Prefer instance-free helpers for pure logic, and small collaborators for logic requiring configurable inputs.

   Pure deterministic helpers can be package-private final classes with static methods or package-private collaborators. Logic requiring `AppProperties`, `Neo4jClient`, repositories, or object mappers should remain in services or become explicitly injected collaborators only when that improves cohesion.

   Alternative considered: make every helper a Spring bean. This is rejected for pure transformations because extra Spring wiring would add complexity without improving testability.

3. Preserve service boundaries around side effects.

   Services should keep Neo4j queries, repository lookups, model client resolution, extraction run lifecycle changes, and transaction-sensitive operations. Extracted utilities should return data needed by services rather than executing side effects themselves.

   Alternative considered: move full workflows into helper classes. This is rejected because it would blur service ownership and make operational behavior harder to reason about.

4. Refactor incrementally by high-value candidates.

   Start with helper clusters that already have meaningful edge cases: extraction validation/filtering, Cypher pattern parsing, graph identity/property filtering, and cleanup result normalization. Then evaluate schema generation normalization helpers if the first pass leaves service complexity high.

   Alternative considered: refactor every `*Service` class in one pass. This is rejected because it increases regression risk and can create churn without improving test coverage.

5. Add focused unit tests before relying on broad service tests.

   New helper tests should cover null/blank handling, schema allow-list behavior, endpoint/key matching, deterministic identity generation, token parsing, and property filtering. Existing service and integration tests should continue to pass to verify behavior preservation at the boundary.

   Alternative considered: keep testing only through existing service tests. This is rejected because the goal is direct coverage of currently private support behavior.

## Risks / Trade-offs

- Behavior drift during extraction -> Mitigation: characterize existing edge cases in helper tests and keep existing service/integration tests passing.
- Over-fragmentation into too many tiny classes -> Mitigation: extract cohesive clusters, not every trivial private method.
- Accidental visibility expansion of internals -> Mitigation: prefer package-private classes/methods unless cross-package reuse requires public visibility.
- Static helper overuse can make future dependency injection harder -> Mitigation: use static methods only for pure logic with explicit inputs and no hidden state.
- Logging may move away from service context -> Mitigation: keep logging decisions in services or pass structured outcomes/reasons from helpers for services to log.
