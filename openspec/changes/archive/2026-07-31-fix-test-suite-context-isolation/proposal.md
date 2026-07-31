## Why

`./mvnw test` no longer provides a reliable complete-regression signal: PostgreSQL-only Spring test contexts still execute Neo4j startup work, and shared relational state can survive the final test in a context and crash recovery in the next context. The regressions currently produce 90 test errors from two root causes, so deterministic context isolation and recovery invariants must be restored without giving up the shared-container performance model.

## What Changes

- Make each integration-test configuration family provision or explicitly disable every startup dependency it activates, so PostgreSQL-only contexts never attempt an unmanaged Neo4j connection.
- Restore deterministic cleanup at Spring-context boundaries as well as between test methods, preventing the last test in one cached context from contaminating the next context.
- Require reprocessing fixtures to preserve plan/item cardinality and counter invariants.
- Harden reprocessing recovery so inconsistent plan totals and authoritative item state cannot be converted into a database-constraint failure that aborts application startup.
- Add regression coverage for context transitions, PostgreSQL-only startup, residual active plans, and the complete `./mvnw test` lane.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `test-suite-execution-performance`: Strengthen shared-infrastructure isolation and configuration-family requirements across Spring context startup and teardown.
- `schema-reprocessing-plans`: Define recovery behavior when persisted plan totals disagree with authoritative reprocessing items.

## Impact

- Affects integration-test container configuration, shared test lifecycle cleanup, relational reprocessing fixtures, and Spring startup runners.
- Affects reprocessing recovery and persistence validation, but does not change public API shapes.
- Keeps the JVM-scoped PostgreSQL and Neo4j container performance architecture and sequential integration execution.
- Requires the default credential-free Maven suite to pass and retain its intended test inventory.
