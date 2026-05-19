## Context

Current schema generation can produce `key: "id"` with properties that do not declare `id`, which later fails strict schema creation validation. Users perceive this as inconsistent because generation appears successful but create/activate fails with key/property mismatch errors.

## Goals / Non-Goals

**Goals:**
- Make generation prompts explicitly constrain key/property consistency.
- Add advisory post-generation checks to surface mismatch warnings and fixes before schema creation.
- Keep advisory behavior non-blocking so generation remains usable for iterative refinement.

**Non-Goals:**
- No change to strict validation rules in schema creation path.
- No automatic mutation of generated schema content in this change.
- No new persistence model or migration.

## Decisions

1. Add hard wording in generation prompt contract.
Reasoning: reduce invalid outputs from the model directly.
Alternative: rely only on post checks; rejected because it misses prevention.

2. Implement generation-time advisory analyzer in service layer.
Reasoning: deterministic server-side check independent of model behavior.
Alternative: ask model to self-validate only; rejected due to nondeterminism.

3. Return warnings/suggestions as additive response metadata.
Reasoning: preserves existing success semantics while improving UX.
Alternative: fail generation when warnings exist; rejected per requirement to keep advisory only.

4. Keep create-schema strict validation unchanged.
Reasoning: preserves data quality guarantees and existing contracts.
Alternative: downgrade create-time mismatch errors to warnings; rejected as unsafe.

## Risks / Trade-offs

- [Risk] Users may ignore warnings and still hit create-time errors. → Mitigation: include precise node-indexed warning text and actionable suggestions.
- [Risk] Prompt rules may slightly reduce model creativity/recall. → Mitigation: constrain only key/property identity rule.
- [Risk] Response DTO expansion may impact strict clients. → Mitigation: additive optional fields only.

## Migration Plan

1. Update schema-generation prompt instructions with key/property contract.
2. Add advisory analyzer for generated schema key/property mismatches.
3. Extend generation response DTO to include warnings/suggestions.
4. Add/adjust tests for warning emission and non-blocking behavior.
5. Verify no regression in existing generation and create paths.

## Open Questions

- Warning format decision: use structured warning objects including at least node index, node label, warning code, message, and suggestions.
- Severity decision: defer adding a `severity` field until additional advisory categories exist.
