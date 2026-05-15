## Context

Graph extraction currently validates relationship triples (`type|fromLabel|toLabel`) as an all-or-nothing check. If one extracted relationship is outside the active schema, validation throws and the whole chunk fails, even when many entities and relationships are valid. Recent logs show this with `HAS_WEBSITE|Organization|URL`.

The extraction prompt already embeds schema JSON, but it does not clearly enforce a closed set of allowed relationship triples in plain language, so model output still includes invented relationships.

## Goals / Non-Goals

**Goals:**
- Prevent chunk failure when only some extracted relationships are schema-invalid.
- Drop invalid relationships and continue processing valid graph data.
- Emit clear warning logs for dropped relationships, including triple and chunk context.
- Reduce invalid relationship generation by listing allowed relationship triples explicitly in the extraction prompt.

**Non-Goals:**
- Accepting unknown node labels.
- Accepting unknown relationship triples into persisted graph data.
- Replacing schema-constrained validation with best-effort free-form extraction.

## Decisions

1. Relationship-level tolerance in validation.
- Decision: During normalization/validation, filter out relationships whose triple is not present in schema definitions, log warnings, and continue.
- Rationale: Preserves useful extraction output while enforcing schema on persisted relationships.
- Alternative considered: Keep fail-fast behavior. Rejected due to high ingestion fragility for long chunks.

2. Keep strict node label checks.
- Decision: Continue to fail when node labels are unknown.
- Rationale: Unknown labels significantly degrade graph consistency and make downstream relationship validation ambiguous.

3. Strengthen prompt with explicit allowed triples.
- Decision: Add a dedicated prompt section listing all allowed relationship triples from the active schema, plus explicit instruction: omit any relationship that does not match one listed triple.
- Rationale: This reduces invalid relationship generation before validation and lowers warning volume.

4. Test both filtering and prompt content.
- Decision: Add tests proving invalid relationships are dropped without chunk failure, and prompt text includes allowed triples plus omit instruction.
- Rationale: Locks behavior and prevents regressions in tolerance and guardrail guidance.

## Risks / Trade-offs

- [Risk] Excessive filtering can hide model quality issues. → Mitigation: warning logs include invalid triple details and counts.
- [Risk] Prompt grows with schema size. → Mitigation: compact triple formatting and existing prompt preview/log sanitization.
- [Risk] Retaining strict node validation can still fail some chunks. → Mitigation: intentional safety boundary; only relationship-level tolerance is relaxed.
