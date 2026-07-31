## 1. Structured Planning

- [ ] 1.1 Define versioned plan, subquestion, metadata constraint, graph request, and plan-summary contracts.
- [ ] 1.2 Implement structured planner prompting/parsing and active-profile invocation with bounded inputs.
- [ ] 1.3 Implement schema/settings validation and deterministic original-query fallback.

## 2. Sufficiency and Follow-up

- [ ] 2.1 Define structured coverage, contradiction, gap, and refinement results.
- [ ] 2.2 Implement post-ranking sufficiency evaluation with bounded evidence excerpts.
- [ ] 2.3 Implement cancellation/deadline/reserved-synthesis gating for one two-query follow-up round.
- [ ] 2.4 Feed follow-up evidence through existing fusion/ranking and persist sanitized round attempts.

## 3. Verification

- [ ] 3.1 Unit-test invalid schema output, bounds, deterministic fallback, sufficiency gates, and follow-up limits.
- [ ] 3.2 Add deterministic scenarios for multi-part questions, contradictions, no evidence, malformed output, and deadline exhaustion.
- [ ] 3.3 Run focused tests and `graphify update .`.
