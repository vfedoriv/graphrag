## Why

Static retrieval cannot reliably decompose multi-part questions or recognize concrete evidence gaps. Advanced search needs a bounded planner and one controlled adaptation step without becoming an open-ended tool-calling agent.

## What Changes

- Generate a structured advanced-search plan from the active KB profile and active schema.
- Validate normalized subquestions, exact identifiers/phrases, metadata constraints, and typed graph requests before retrieval.
- Evaluate post-ranking coverage, contradictions, and concrete evidence gaps.
- Permit at most one follow-up round with two refined queries when cancellation and remaining deadline allow it.
- Persist plan summaries, rounds, prompt revisions, and sanitized attempt diagnostics in the durable run.

## Capabilities

### New Capabilities

- `advanced-search-planning`: Schema-aware bounded planning, evidence sufficiency evaluation, and one conditional follow-up round.

### Modified Capabilities

None.

## Impact

This adds structured model contracts/parsers, plan validation, stage orchestration, follow-up gating, prompt revisions, and deterministic fallback tests. It is proposal 5 of 7 and depends on durable runs and ranked evidence; it does not synthesize the final answer.
