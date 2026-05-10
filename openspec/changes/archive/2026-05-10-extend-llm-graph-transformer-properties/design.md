## Context

Schema generation depends on graph extraction output from LangChain4j `LLMGraphTransformer`. In the current behavior, node and edge properties are often left empty, even when model output contains property payloads. This creates quality loss in generated schemas and forces fragile downstream inference heuristics.

The change introduces an explicit transformer extension in our codebase that controls `transform()` behavior and property extraction, while keeping existing endpoint and service contracts stable.

## Goals / Non-Goals

**Goals:**
- Implement `LLMGraphTransformerExt` with overridden `transform()` that preserves node and edge properties from model output.
- Integrate `LLMGraphTransformerExt` into schema generation flow.
- Add deterministic unit tests for property extraction using representative payload patterns.

**Non-Goals:**
- Changing REST API request/response shapes.
- Replacing LangChain4j end-to-end.
- Expanding extraction into new semantic fields beyond node/edge properties.

## Decisions

Use inheritance (`LLMGraphTransformerExt extends LLMGraphTransformer`) and override `transform()`.
Rationale: user-requested approach and minimal call-site disruption, while retaining compatibility with current builder/model configuration.

Mirror known-good property handling behavior from Python example fixtures.
Rationale: reduce ambiguity in expected output and align with an existing reference implementation.

Cover extension logic with narrow unit tests.
Rationale: this is internal parsing/mapping logic; unit tests provide stable regression guardrails without requiring end-to-end LLM calls.

## Risks / Trade-offs

- Upstream `LLMGraphTransformer` internal changes could break subclass assumptions -> Mitigation: keep extension surface minimal and add regression tests around expected output mapping.
- Property key/value normalization differences vs Python behavior -> Mitigation: codify normalization rules in tests and document expected handling in class-level comments.
- Duplicate logic divergence from upstream implementation -> Mitigation: isolate only the property-handling delta and preserve original flow where possible.

## Migration Plan

- Add `LLMGraphTransformerExt` class.
- Update schema generation service to instantiate extension.
- Add unit tests for extension behavior.
- Run targeted and full test suite.
- No data migration or API migration required.

## Open Questions

- Whether relationship `description` should remain represented as dedicated schema description field only, or also kept in relationship properties map when present in raw output.
