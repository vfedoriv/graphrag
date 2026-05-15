## Context

`SpringAiGraphExtractionClient` deserializes LLM JSON into `GraphExtractionResult`. Current deserialization is strict and rejects unknown fields, which causes extraction to fail when the LLM includes additional keys (for example node-level `id`). These additional keys are not required by downstream graph persistence, but strict parsing currently turns them into hard failures.

## Goals / Non-Goals

**Goals:**
- Prevent extraction failure caused only by unknown JSON fields in model output.
- Keep safety guarantees by continuing schema-constrained validation after parsing.
- Improve observability by emitting warnings when unknown fields are ignored.
- Add deterministic test coverage for tolerant parsing behavior.

**Non-Goals:**
- Accepting invalid labels, relationship types, or properties that violate active schema constraints.
- Redesigning the entire extraction prompt or graph data model.
- Silent failure handling without logs.

## Decisions

1. Use tolerant JSON deserialization for extraction payloads.
- Decision: Configure the extraction parser path to ignore unknown properties for `GraphExtractionResult` DTOs.
- Rationale: Unknown keys from LLMs are common and should not abort ingestion when required fields are present.
- Alternative considered: Keep strict mapper and strip keys with regex/string preprocessing. Rejected due to fragility and higher maintenance risk.

2. Preserve strict domain validation after deserialization.
- Decision: Continue existing validation layer to enforce allowed labels/relationship types/properties and required structure.
- Rationale: Parsing tolerance should not weaken schema safety.
- Alternative considered: Relax validation in tandem with parser tolerance. Rejected because it broadens data quality risk.

3. Emit actionable warnings for ignored fields.
- Decision: Log warning entries including document/chunk context and compact set of unknown keys when available.
- Rationale: Operators need drift visibility for prompt tuning and model behavior monitoring.
- Alternative considered: No warnings. Rejected because silent tolerance hides model-output regressions.

4. Add tests for tolerant parsing and validation boundaries.
- Decision: Add tests proving unknown keys are ignored while schema violations still fail.
- Rationale: Prevent regressions in both robustness and safety.

## Risks / Trade-offs

- [Risk] Tolerant parsing may mask prompt quality regressions. → Mitigation: warning logs and optional metrics counter for ignored keys.
- [Risk] Overly broad ignore configuration could affect unrelated DTO parsing. → Mitigation: scope tolerance to extraction path/DTO only, not global mapper behavior.
- [Risk] Larger malformed payloads could still parse partially. → Mitigation: retain required-field checks and downstream validation before persistence.
