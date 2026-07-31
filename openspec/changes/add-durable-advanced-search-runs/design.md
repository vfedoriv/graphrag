## Context

The repository already has PostgreSQL-backed durable workflows with state transitions, recovery, pagination, and knowledge-base ownership. Advanced search adds a shorter-lived workflow with bounded executor admission and cooperative cancellation of parallel branches.

This is proposal 4 of 7 and follows `add-advanced-search-fusion-reranking`. Later planning and synthesis proposals fill additional stages without changing the run resource.

## Goals / Non-Goals

**Goals:**

- Return immediately with a durable run resource and reliable polling routes.
- Keep all operational state in PostgreSQL and all retrieved graph data in Neo4j.
- Bound concurrency, queueing, deadlines, stored excerpts, and retention.

**Non-Goals:**

- Replay model calls after restart or provide resumable mid-stage execution.
- Stream tokens or expose percentage progress.
- Define planner, sufficiency, or synthesis prompts.

## Decisions

### Persist three bounded aggregates

`advanced_search_run` stores ownership, input, status/stage, snapshots, claim, deadline, version, failure category, and lifecycle timestamps. `advanced_search_attempt` stores content-free per-round/subquery/retriever outcomes. `advanced_search_result` stores one validated bounded JSONB result snapshot. Full chunks, raw prompts, and model responses are not duplicated.

### Admit before row creation

A dedicated executor allows two active runs and fifty queued runs by default. Capacity is reserved before inserting a run; exhaustion returns `429` and leaves no orphan row. Branch work has a separate four-task-per-run bound.

### Use explicit stages and cooperative checks

Workers atomically claim queued rows and persist status, stage, and counters. They check cancellation and the absolute deadline between stages and pass the smaller remaining timeout to Neo4j/model calls. Late branch results are ignored after terminal transition.

### Interrupt rather than replay after restart

Startup recovery marks stale `QUEUED` or `RUNNING` rows `INTERRUPTED`. Clients can submit a new run; automatic replay is excluded because provider effects and snapshots may no longer be reproducible.

## Risks / Trade-offs

- [Admission reservation and row creation diverge] → Encapsulate both and release capacity on transaction failure.
- [Cancellation races with completion] → Use optimistic terminal transitions and make terminal states immutable.
- [JSONB contracts drift] → Version and validate result payloads before persistence and readback.
- [Cleanup removes a result during polling] → Use terminal expiry timestamps and return `404` consistently after deletion.

## Migration Plan

1. Add nullable-free tables, repositories, state machine, and ownership checks.
2. Add executor admission, claims, deadlines, cancellation, and result validation.
3. Expose polling APIs and scheduled cleanup.
4. Add startup interruption recovery and failure-race tests.
5. Roll back endpoint exposure while retaining readable terminal rows until TTL cleanup.

## Open Questions

None.
