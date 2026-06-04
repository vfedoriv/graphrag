## Context

The current test suite already covers many major paths: schema parsing and validation, node key support, controller contracts, document upload and processing, graph extraction and write support, Cypher generation, validation and execution, observability, storage, configuration, and several Neo4j-backed integration flows. The remaining risk is not a lack of test structure, but the possibility that important boundary, error, cleanup, persistence, and wiring paths are unevenly covered.

The implementation should therefore start with an audit of production classes against existing tests, then add only high-signal tests. The project constraints still apply: use Java 25, Spring Boot test conventions already present in the repo, Maven Wrapper, mocked AI clients, Testcontainers for Neo4j integration tests, and concrete Java types instead of `var`.

## Goals / Non-Goals

**Goals:**

- Identify meaningful unit and integration coverage gaps in current production behavior.
- Add focused tests for high-risk missing paths.
- Preserve deterministic test execution without external AI provider credentials.
- Keep test style consistent with existing test classes and fixtures.
- Run the relevant targeted tests and, where feasible, the full `./mvnw test` suite.

**Non-Goals:**

- Introducing an arbitrary coverage percentage gate.
- Rewriting existing tests solely for style.
- Adding tests for trivial getters, enum constants, framework plumbing, or duplicate scenarios.
- Changing public API behavior unless a new test exposes a defect that must be fixed.
- Adding new runtime dependencies.

## Decisions

1. Audit by production surface before writing tests.

   Rationale: the project already has many tests, so adding tests without mapping current coverage risks duplication. The audit should group production code by controller, service, graph, schema, query, storage, config, repository, observability, and document parsing areas, then compare each group with existing tests.

   Alternative considered: generate broad coverage reports and chase uncovered lines. This is weaker because uncovered lines may be low-value, while covered lines can still miss important behavioral branches.

2. Prefer unit tests for deterministic branch behavior.

   Rationale: parsing, normalization, validation, cleanup selection, Cypher safety checks, and exception mapping can usually be tested quickly without Spring context or Neo4j. These tests give precise failure signals.

   Alternative considered: cover these through integration flows only. Integration coverage is useful but slower and less precise for branch-level behavior.

3. Use integration tests only where wiring or persistence is the risk.

   Rationale: controller contracts, request validation, multipart upload, Neo4j persistence, transaction behavior, and repository queries need the real Spring/Testcontainers setup. Existing integration tests provide patterns that should be extended instead of introducing a new harness.

   Alternative considered: mock repositories for all services. That would miss the Cypher and Neo4j semantics most likely to regress.

4. Keep AI behavior mocked.

   Rationale: tests must pass without `OPENAI_API_KEY`, `LM_STUDIO_API_KEY`, or network access. Existing fake or mocked AI client patterns should be reused for extraction, schema generation, embeddings, and Cypher generation.

   Alternative considered: profile-gated live-provider tests. Those are not appropriate for required CI/local test coverage.

## Risks / Trade-offs

- Coverage audit misses an important path -> Mitigation: inspect both `src/main/java` and `src/test/java`, use existing integration flow names as anchors, and prioritize behavior with business or data-safety impact.
- New integration tests make the suite slower -> Mitigation: prefer unit tests unless Spring wiring, HTTP contract, or Neo4j semantics are central to the behavior.
- Tests over-specify implementation details -> Mitigation: assert externally visible outcomes, persisted state, exceptions, or validation results rather than private call order.
- Newly added tests expose existing defects -> Mitigation: fix production code narrowly only where the observed behavior contradicts existing specs or intended contracts.

## Migration Plan

No runtime migration is required. The change is limited to tests and any narrow production fixes needed to satisfy existing behavior contracts. Rollback is removal of the added tests and any associated narrow fixes.

## Open Questions

- Should future work introduce an automated coverage report threshold, or should this change remain focused on qualitative coverage gaps only?
