## 1. Execution Policy and Validation

- [x] 1.1 Define an immutable per-request query policy from live runtime settings and use it in validation, generation, ask, and execution.
- [x] 1.2 Replace presence-only limit matching with syntax-aware top-level limit and bound-parameter validation.
- [x] 1.3 Inject missing limits and reject explicit limits above the configured maximum with stable validation details.
- [x] 1.4 Return the applied policy snapshot from every query response path.

## 2. Database Execution

- [x] 2.1 Execute `EXPLAIN` and user Cypher through the existing application Neo4j driver with a transaction timeout.
- [x] 2.2 Map deadline failures to RFC 7807 responses without raw query content.
- [x] 2.3 Remove dedicated query credentials, principal provisioning, and read-only access health configuration.

## 3. Verification and Documentation

- [x] 3.1 Add adversarial validation tests for explicit, bound, literal, and comment-contained limit text.
- [x] 3.2 Add integration tests proving oversized limits and deadline enforcement.
- [x] 3.3 Update contributor documentation for the enforced query policy and breaking API behavior.
- [x] 3.4 Run focused query tests and the full Maven suite.
