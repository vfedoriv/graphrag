## 1. API Contract

- [x] 1.1 Add `canRetry` to analysis-run detail and history summary DTOs while retaining `retryable` as persisted failure classification.
- [x] 1.2 Update OpenAPI contract assertions and API documentation to distinguish `retryable` from `canRetry` and call out the analysis-history migration.

## 2. Shared Retry Eligibility

- [x] 2.1 Introduce a typed analysis retry-eligibility decision covering terminal run status, open draft state, active-source availability, and absence of another running analysis.
- [x] 2.2 Reuse draft-wide eligibility inputs across a history page so deriving `canRetry` does not add per-run repository queries.
- [x] 2.3 Map history `retryable` from the persisted run value and map `canRetry` from the shared eligibility decision.
- [x] 2.4 Map analysis detail `canRetry` from the same eligibility decision without changing its persisted `retryable` value.
- [x] 2.5 Apply the shared decision in retry-command validation while preserving completed-run retry, permanent-failure retry, lineage, reuse, optimistic revision checks, start idempotency, and authoritative race/capacity validation.

## 3. Verification

- [x] 3.1 Add service tests for completed, retryable-failure, permanent-failure, running, closed-draft, and no-active-source combinations of `retryable` and `canRetry`.
- [x] 3.2 Add integration coverage proving detail and history agree on both fields and that frontend action eligibility comes from `canRetry`.
- [x] 3.3 Add retry endpoint coverage for accepted completed/permanent-failure runs and rejected running, closed-draft, stale-revision, and no-active-source requests with no unintended run creation.
- [x] 3.4 Run focused schema-draft tests and the full `./mvnw test` suite.
- [x] 3.5 Run `graphify update .` after implementation and verify the generated knowledge graph is current.
