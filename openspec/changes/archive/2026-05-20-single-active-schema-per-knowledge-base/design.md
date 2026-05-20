## Context

Schemas are versioned per knowledge base and one schema is used as the active contract for extraction and query validation. Current activation behavior does not formally guarantee exclusivity, which can lead to ambiguous active-schema resolution in read paths. The repository already treats schema versions as immutable, so this change only affects activation state transitions.

## Goals / Non-Goals

**Goals:**
- Ensure exactly one active schema per knowledge base after any successful activation call.
- Make activation idempotent when the requested schema is already active.
- Keep activation changes scoped to one knowledge base and avoid cross-knowledge-base side effects.
- Preserve existing API shape while tightening activation semantics.

**Non-Goals:**
- Changing schema immutability or schema content validation rules.
- Adding multi-active or staged rollout schema modes.
- Changing query validation logic beyond clarifying its active-schema precondition.

## Decisions

1. Perform activation as an atomic state transition in the schema activation service/repository path.
Rationale: activation must not leave partial state where multiple schemas remain active due to mid-flight failure.
Alternative considered: best-effort deactivate then activate in separate operations; rejected because it can produce inconsistent state during failures.

2. Deactivate sibling schemas by knowledge-base scope during activation of the target schema.
Rationale: this enforces exclusivity directly in write flow and avoids relying on read-time tie-breaking.
Alternative considered: keep multiple active rows and choose latest at read time; rejected because behavior becomes implicit and harder to reason about.

3. Keep repeated activation of the same schema as a no-op success.
Rationale: clients may retry and should receive deterministic behavior without unnecessary writes.
Alternative considered: return conflict/error for already active target; rejected because it creates needless client complexity.

4. Clarify cypher-validation dependency on unique active schema per knowledge base.
Rationale: validation must operate against one unambiguous schema contract.
Alternative considered: no spec delta; rejected because the new invariant affects capability-level assumptions.

## Risks / Trade-offs

- [Risk] Concurrent activation requests for different schemas in the same knowledge base could race. → Mitigation: execute activation/deactivation in one transactional boundary and validate final active target.
- [Risk] Bulk deactivation update may touch more records per activation. → Mitigation: constrain update by knowledge base id and skip writes when target already active.
- [Risk] Existing data with multiple active schemas may violate new invariant until first activation. → Mitigation: include test coverage and (if needed) a one-time reconciliation path in implementation.
