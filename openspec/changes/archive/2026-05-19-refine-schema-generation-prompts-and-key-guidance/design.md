## Context

Schema generation currently uses two prompt layers:
- `LLMGraphTransformerExt.USER_TEMPLATE` shows property examples with array values (for example `"head_properties": {"name": ["..."]}`), while downstream code expects plain key/value semantics for `GraphNode.properties` and `GraphEdge.properties`.
- `LangChain4jSchemaGenerationService` injects `SCHEMA_PROMPT_CONTRACT` focused on strict key/property matching, but practical model behavior shows weak guidance for selecting realistic identity fields.

This creates prompt ambiguity and unstable key choices, which then surface as advisory warnings or create-time validation failures.

## Goals / Non-Goals

**Goals:**
- Make property shape instructions unambiguous: `head_properties`, `relation_properties`, `tail_properties` are JSON objects with string key/value pairs.
- Replace/extend strict key-language with clearer canonical-identity guidance and examples for node types.
- Support composite identity guidance where generated node `key` may contain multiple property names to be used as a combined unique key in later code paths.
- Keep server-side advisory validation deterministic and non-blocking.
- Reconcile tests and prompt assertions with the updated contract.

**Non-Goals:**
- No API breaking changes.
- No automatic schema mutation to fix invalid keys.
- No removal of existing key/property advisory warnings.

## Decisions

1. Update USER_TEMPLATE examples to scalar string property values.
- Rationale: remove array implication and align with intended property map semantics.
- Alternative considered: keep array examples and rely on parser flattening. Rejected due to recurring model confusion.

2. Reframe SCHEMA_PROMPT_CONTRACT to include canonical key heuristics.
- Rationale: model should propose plausible identity properties (or combinations) from declared properties rather than defaulting to generic `id`.
- Alternative considered: keep only hard constraint language. Rejected because it is not sufficiently actionable for model generation quality.

3. Treat generated node `key` as either single property name or list of property names.
- Rationale: several domains require composite identity, and prompt guidance should allow listing key components explicitly.
- Alternative considered: force single key property only. Rejected because it cannot represent robust identity for common entities.

4. Keep advisory mismatch logic in place, but align wording/suggestions to updated prompt semantics.
- Rationale: deterministic server checks remain necessary even with improved prompts.
- Alternative considered: remove warnings after prompt improvements. Rejected as unsafe and non-deterministic.

5. Do not keep compatibility for model outputs that send array-valued properties in schema generation prompts/results.
- Rationale: prompt and contract are intentionally strict; property values must be scalar strings in key/value maps.
- Alternative considered: tolerant array flattening. Rejected per requirement.

## Risks / Trade-offs

- [Risk] Model may still produce weak keys despite stronger prompt examples. → Mitigation: keep advisory warnings and assert prompt contract coverage in tests.
- [Risk] Overly prescriptive examples might bias domains. → Mitigation: position examples as heuristics, not mandatory fixed templates.
- [Risk] Existing tests coupled to array-shaped prompt snippets may break. → Mitigation: update tests to enforce strict scalar property-value contract.

## Migration Plan

1. Update prompt templates in `LLMGraphTransformerExt` and `LangChain4jSchemaGenerationService`.
2. Update extraction/parsing logic to enforce scalar string property values for schema-generation property maps.
3. Update or add tests for prompt wording, strict property-shape expectations, single/composite key guidance, and advisory behavior.
4. Run targeted schema-generation tests, then full test suite.

## Open Questions

- How composite keys should be represented in final schema JSON contract (`key` array vs serialized convention) and validated in create-time schema validator will be implemented in this change.
