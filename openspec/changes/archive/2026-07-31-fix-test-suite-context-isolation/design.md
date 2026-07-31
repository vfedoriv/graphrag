## Context

The integration suite deliberately reuses one PostgreSQL container and one Neo4j container per Maven test JVM. That optimization makes cleanup part of the test contract: data survives Spring context eviction, and application runners execute before a test method's `@BeforeEach` cleanup.

Two regressions violate that contract. PostgreSQL-only context families omit Neo4j connection details but still run `GraphSchemaInitializer`. Separately, the final test in `SchemaWorkflowRelationalRepositoryIntegrationTest` can leave a queued plan with a declared document count but no item rows; the next context runs recovery before cleanup, derives zero counters while retaining the declared total, and violates the relational count constraint.

The design must retain shared-container performance, sequential integration execution, production startup behavior, and database invariants.

## Goals / Non-Goals

**Goals:**

- Make each test context family internally complete: graph startup work runs only when that family provides graph infrastructure.
- Restore a clean shared-state boundary after every integration test, including the last method before a Spring context transition.
- Keep reprocessing fixtures structurally valid and representative of production transactions.
- Make recovery recompute all derived cardinality and counters from authoritative item rows without aborting application startup.
- Prove the result with targeted regressions and the complete credential-free suite.

**Non-Goals:**

- Revert JVM-scoped shared containers or reintroduce per-class containers.
- Enable parallel integration-test execution.
- Weaken PostgreSQL constraints or suppress unexpected persistence exceptions globally.
- Change public reprocessing endpoints, DTO shapes, or normal plan creation semantics.
- Modify unrelated warning/error logs intentionally exercised by negative-path tests.

## Decisions

### Keep explicit PostgreSQL-only and full-store context families

PostgreSQL-only tests will continue to avoid starting Neo4j. Their shared test configuration will explicitly replace or disable graph startup initialization before application runners execute. Full-store integration contexts will continue to provision the shared Neo4j container and execute the real initializer.

This preserves the performance intent of `ba18bd8` while making dependencies explicit. Starting Neo4j for every relational test was rejected because it erases the purpose of the PostgreSQL-only family. Depending on an operator-managed `localhost:7687` was rejected because the suite must remain self-contained.

### Clean shared mutable state after each test

The common integration-test lifecycle will restore relational, graph, filesystem, runtime-setting, and stateful-double baselines after each test, not only before the next test method. Setup cleanup may remain as defensive protection, but teardown is the boundary that prevents a final method from leaking state into a newly created context whose startup runners execute before JUnit setup.

Cleanup will remain scoped to the shared test stores and continue to run sequentially. Context invalidation is not a substitute because the containers intentionally outlive their contexts.

### Preserve valid reprocessing fixtures

Tests that exercise active-plan uniqueness will create a plan whose item cardinality and counters agree, or will explicitly terminalize/clean the plan before completion. Test fixtures will not rely on temporarily invalid cross-table states that normal transactional plan creation would not expose.

This gives uniqueness tests production-shaped state while teardown remains the primary isolation guarantee.

### Treat persisted item rows as authoritative during counter repair

Recovery will recompute `totalDocuments` together with queued, running, succeeded, failed, stale, and blocked counts from the persisted item set. It will derive terminal plan status only after those values agree. A metadata-only warning will record a detected total/item-cardinality mismatch without document content.

Keeping the stale declared total was rejected because it can produce a constraint-invalid update. Weakening the database constraint was rejected because it would allow invalid progress responses. Counting missing rows as failed was rejected because no authoritative per-document outcome exists for those rows.

### Isolate recovery failures per plan

Where a malformed plan still cannot be repaired, recovery will report the plan identifier and exception class and continue safely rather than making an unrelated Spring context unusable. This is a last-resort containment boundary, not a replacement for invariant-preserving repair; database connectivity and schema migration failures remain startup-fatal.

### Verify both regression boundaries

Targeted tests will cover:

- PostgreSQL-only context startup with no Neo4j listener.
- Full-store context startup and real graph schema initialization.
- Transition from a mutating context to another context over the shared database.
- Recovery of mismatched stored totals and item cardinality.
- Active-plan uniqueness with valid fixtures.

The final acceptance check is `./mvnw test`, followed by the fast lane where useful.

## Risks / Trade-offs

- **[Teardown failure can obscure the original test failure]** → Preserve the original exception where possible and attach cleanup failures as additional diagnostics.
- **[A no-op graph initializer could hide accidental graph access in PostgreSQL-only tests]** → Disable only startup initialization; unexpected graph service use must still fail rather than receiving a general-purpose fake graph store.
- **[Recomputing total count can conceal historical corruption]** → Emit a metadata-only warning with plan ID, stored total, and actual item count, and cover the behavior explicitly.
- **[Per-plan recovery containment could mask systemic failures]** → Catch only plan-local data/invariant failures; leave infrastructure and migration failures fatal.
- **[Additional teardown increases suite time]** → Reuse the existing bounded cleanup operations and confirm the performance contract remains within the established shared-container target.

## Migration Plan

1. Add focused failing regression tests for both root causes.
2. Correct PostgreSQL-only context startup composition.
3. Add teardown isolation and repair invalid uniqueness fixtures.
4. Harden reprocessing counter/cardinality recovery and plan-local containment.
5. Run targeted integration tests, `./mvnw test -Pfast`, and the full `./mvnw test` suite.

No production data migration or API migration is required. If the changes regress startup or test isolation, revert the test configuration and recovery changes together; existing Flyway constraints remain unchanged.

## Open Questions

- Whether defensive setup cleanup should remain once teardown is proven reliable, or be retained to improve recovery after an externally interrupted test JVM.
- Whether plan-cardinality mismatch metrics should be added in addition to the required metadata-only warning.
