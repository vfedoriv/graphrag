## Why

A 60-second multi-stage search cannot be exposed safely as one synchronous request: clients need durable status, cancellation, bounded admission, and recoverable terminal artifacts. PostgreSQL must remain authoritative for this operational lifecycle.

## What Changes

- Add durable run, retriever-attempt, and validated-result persistence with KB ownership and cascade cleanup.
- Add asynchronous create/list/status/result/cancel endpoints with `202`, `409`, `404`, and `429` semantics.
- Add explicit statuses, stages, counters, optimistic transitions, atomic worker claims, cooperative cancellation, and deadline checks.
- Add startup interruption recovery and scheduled terminal-run retention cleanup.
- Add typed live and restart-required `app.advanced-search.*` lifecycle settings and immutable per-run snapshots.

## Capabilities

### New Capabilities

- `advanced-search-runs`: Durable advanced-search admission, lifecycle, persistence, polling, cancellation, recovery, and retention.

### Modified Capabilities

- `runtime-application-settings`: Exposes validated advanced-search lifecycle settings with correct live versus restart behavior.

## Impact

This adds Flyway migrations, relational entities/repositories, a bounded run executor, recovery/cleanup jobs, controller DTOs, RFC 7807 mappings, and integration tests. It is proposal 4 of 7 and consumes the ranking pipeline from proposal 3 through an orchestration boundary; it does not define adaptive planning or answer synthesis.
