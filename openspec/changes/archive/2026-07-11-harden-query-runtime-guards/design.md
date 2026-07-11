## Context

Query validation checks blocked keywords, schema references, and `EXPLAIN`, but only injects a row limit when `LIMIT` is absent. Execution uses the general application Neo4j client without an execution timeout. Validation responses constructed by the controller use startup `AppProperties` rather than the live runtime settings used by execution.

## Goals / Non-Goals

**Goals:**
- Enforce row and time limits for every user-executable query.
- Apply query deadlines without introducing another Neo4j driver, credential set, or principal.
- Return the effective runtime policy from validation, generation, ask, and execution paths.
- Preserve schema-aware validation as defense in depth.

**Non-Goals:**
- Support arbitrary write Cypher for any public endpoint.
- Replace generated-query UX or change domain schema semantics.
- Add or manage a separate Neo4j principal for public query execution.

## Decisions

### Build one immutable per-request query policy

Read runtime query settings once at request start and pass an immutable policy through validation and execution. The policy contains maximum rows, deadline, limit requirement, and blocked keywords, preventing divergent re-reads and ensuring responses report exactly the policy applied.

### Enforce limits with syntax-aware top-level limit handling

Use a lexical Cypher scanner that ignores comments and literals to identify executable top-level `LIMIT` clauses and bound limit parameters. Missing limits are injected; numeric or bound limits above the policy maximum are rejected. This is preferred over a global regular expression, which cannot distinguish executable syntax from text and cannot reliably constrain explicit limits.

### Reuse the application Neo4j driver

Run validation `EXPLAIN` and execution through the auto-configured application `Driver` and configured database. This avoids a second driver lifecycle, duplicate credentials, a provisioning sidecar, and a dedicated query principal. Blocked-keyword and schema-aware validation remain the barriers against write Cypher.

### Enforce transaction timeout at the driver boundary

Apply the runtime deadline to the Neo4j transaction configuration for validation and execution. Timeout errors map to a stable problem response and include no raw query content.

## Risks / Trade-offs

- [Cypher syntax is broad] → Limit scanner and blocked-keyword validation remain narrowly scoped and backed by adversarial tests.
- [Timeout support varies by client configuration] → Verify the exact Spring Data Neo4j/driver API against current official documentation during implementation.
- [Explicit high limits become errors] → Return a clear validation error with the effective configured maximum.

## Migration Plan

1. Ship policy reporting and syntax-aware limit validation behind focused tests.
2. Apply the policy timeout to `EXPLAIN` and execution through the existing application driver.
3. Update API documentation and remove the legacy controller path that reads startup-only query values.

## Open Questions

- What API status and retry guidance should represent a query deadline exceeded?
