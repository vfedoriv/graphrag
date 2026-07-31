## Context

The application already resolves profile-scoped chat clients and active schemas. Advanced planning must translate a user question into bounded retriever inputs while keeping all executable graph behavior inside the typed graph-plan compiler.

This is proposal 5 of 7 and requires `add-durable-advanced-search-runs` plus proposals 1–3.

## Goals / Non-Goals

**Goals:**

- Decompose at most three initial subqueries and capture exact/metadata/graph intent.
- Validate model output before any branch execution.
- Run at most one evidence-driven follow-up round with at most two refinements.

**Non-Goals:**

- Expose or execute model-generated Cypher.
- Add a generic tool loop, web search, or cross-KB retrieval.
- Generate the public answer.

## Decisions

### Use one versioned structured plan

The plan contains normalized subquestions, exact phrases/identifiers, optional filename/content-type constraints, and typed graph requests. It snapshots schema/profile/settings/prompt identities already carried by the run. Validation rejects unknown schema elements and enforces all counts and string bounds.

### Fall back to a deterministic single query

If planning output is invalid or the model is unavailable but retrieval time remains, the original query becomes one text subquery with no graph or metadata constraints. The attempt records fallback use without exposing model content.

### Require a concrete gap for follow-up

The sufficiency result reports subquestion coverage, contradictions, missing evidence, and proposed refinements. A follow-up occurs only for a validated concrete gap, with remaining time above a configured floor and no cancellation. Its evidence is fused through the same ranking pipeline.

## Risks / Trade-offs

- [Planner invents schema identifiers] → Validate against the snapshotted active schema and discard invalid fields or use deterministic fallback.
- [Follow-up consumes synthesis time] → Gate on remaining deadline and reserve a synthesis budget.
- [Evaluator overstates sufficiency] → Preserve coverage/contradiction diagnostics and versioned fixtures.

## Migration Plan

1. Add structured plan and sufficiency schemas plus validators.
2. Integrate planning before retrieval with deterministic fallback.
3. Integrate evaluation after ranking and one gated follow-up round.
4. Persist content-free summaries and round attempts in existing run tables.

## Open Questions

None.
