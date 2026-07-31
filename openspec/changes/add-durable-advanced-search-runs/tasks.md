## 1. Persistence and State

- [ ] 1.1 Add Flyway tables, constraints, indexes, KB cascades, optimistic versions, and bounded JSONB result versioning.
- [ ] 1.2 Add relational entities, ports, adapters, ownership queries, paging, and atomic worker claims.
- [ ] 1.3 Implement legal status/stage transitions, counters, failure categories, deadlines, and immutable snapshots.

## 2. Execution Lifecycle

- [ ] 2.1 Implement pre-row bounded admission and dedicated run/branch executors.
- [ ] 2.2 Implement cooperative cancellation, remaining-time propagation, and late-result rejection.
- [ ] 2.3 Implement startup interruption recovery and terminal retention cleanup.
- [ ] 2.4 Add typed live/restart advanced-search settings and atomic cross-setting validation.

## 3. API and Verification

- [ ] 3.1 Add create, paginated list, status, result, and idempotent cancel endpoints with owned navigation links.
- [ ] 3.2 Map unavailable results, capacity, invalid bounds, and ownership to RFC 7807 responses.
- [ ] 3.3 Unit-test transitions, deadline math, admission, cancellation races, JSON validation, and settings snapshots.
- [ ] 3.4 PostgreSQL/controller integration-test claims, recovery, paging, KB cascades, retention, and `202`/`409`/`429` behavior.
- [ ] 3.5 Run focused and full credential-free tests, then `graphify update .`.
