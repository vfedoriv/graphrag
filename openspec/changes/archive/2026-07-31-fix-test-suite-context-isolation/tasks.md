## 1. Reproduce and Guard the Regressions

- [x] 1.1 Add a regression test proving the PostgreSQL-only Spring context starts without provisioning or connecting to Neo4j.
- [x] 1.2 Add a regression test proving the full-store context still runs real graph schema initialization against the shared Neo4j container.
- [x] 1.3 Add a context-transition regression that leaves mutating test state and verifies cleanup occurs before another context's startup runners execute.
- [x] 1.4 Add recovery tests for stored total/item-cardinality mismatches and for isolation of an unrecoverable plan-local inconsistency.

## 2. Correct Integration Context Composition

- [x] 2.1 Update the PostgreSQL-only integration configuration family to disable only graph startup initialization while leaving unexpected graph data-plane access visible as a failure.
- [x] 2.2 Verify full-store tests continue to receive both shared connection-detail providers and initialize Neo4j schema state normally.
- [x] 2.3 Consolidate relational-only test annotations or configuration declarations so new tests cannot silently omit the graph-startup policy.

## 3. Restore Shared-State Isolation

- [x] 3.1 Extend the common integration lifecycle to clean relational, graph, filesystem, runtime-setting, and stateful-double state after every applicable test.
- [x] 3.2 Preserve useful diagnostics when both a test and its teardown cleanup fail.
- [x] 3.3 Make active reprocessing-plan uniqueness fixtures preserve plan/item cardinality and counter invariants.
- [x] 3.4 Audit integration classes using the shared containers for final-test state that currently relies only on the next method's setup cleanup.

## 4. Harden Reprocessing Recovery

- [x] 4.1 Recompute `totalDocuments` and every outcome counter from authoritative persisted items in the same invariant-preserving repair operation.
- [x] 4.2 Derive terminal plan status only after repaired total and outcome counters agree.
- [x] 4.3 Emit a metadata-only warning when stored total and item cardinality differ.
- [x] 4.4 Contain plan-local malformed-state failures so unrelated plan recovery and application startup can continue, while keeping infrastructure and migration failures fatal.

## 5. Verification

- [x] 5.1 Run focused PostgreSQL-only context, full-store context, shared-state transition, uniqueness, and recovery integration tests.
- [x] 5.2 Run `./mvnw test -Pfast` and confirm deterministic non-container coverage passes.
- [x] 5.3 Run `./mvnw test` and confirm the complete credential-free suite passes with no context-threshold cascades.
- [x] 5.4 Run the suite performance measurement and confirm shared-container start counts and the established performance contract remain satisfied.
- [x] 5.5 Run `graphify update .` after code changes and review the final diff for unrelated modifications or content-bearing logs.
