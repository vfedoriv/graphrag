## 1. Structured Planning

- [x] 1.1 Define versioned plan, subquestion, metadata constraint, graph request, and plan-summary contracts.
- [x] 1.2 Implement structured planner prompting/parsing and active-profile invocation with bounded inputs.
- [x] 1.3 Implement schema/settings validation and deterministic original-query fallback.

## 2. Sufficiency and Follow-up

- [x] 2.1 Define structured coverage, contradiction, gap, and refinement results.
- [x] 2.2 Implement post-ranking sufficiency evaluation with bounded evidence excerpts.
- [x] 2.3 Implement cancellation/deadline/reserved-synthesis gating for one two-query follow-up round.
- [x] 2.4 Feed follow-up evidence through existing fusion/ranking and persist sanitized round attempts.

## 3. Verification

- [x] 3.1 Unit-test invalid schema output, bounds, deterministic fallback, sufficiency gates, and follow-up limits.
- [x] 3.2 Add deterministic scenarios for multi-part questions, contradictions, no evidence, malformed output, and deadline exhaustion.
- [x] 3.3 Run focused tests and `graphify update .`.
