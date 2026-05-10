## Context

Schema generation relies on `LLMGraphTransformer` output that includes entities, relationships, optional descriptions, and optional properties. The current `inferSchema` mapping keeps labels and relationship types but does not reliably map node descriptions, node properties, or edge descriptions into the generated YAML model.

This is a service-level correctness gap rather than an API contract issue. The existing endpoints and request/response shapes can stay unchanged.

## Goals / Non-Goals

**Goals:**
- Preserve node descriptions from transformer output in generated schema.
- Preserve node properties from transformer output in generated schema, including consistent property naming and type mapping.
- Preserve edge descriptions from transformer output in generated schema.
- Add tests that fail when these fields are dropped and pass when correctly mapped.

**Non-Goals:**
- Changing schema-generation endpoint contracts.
- Changing example-generation flows.
- Expanding property inference semantics beyond data already available in transformer output.

## Decisions

Map transformer output to schema model with explicit metadata propagation.
Rationale: Minimal blast radius and no dependency changes. This fixes data loss at the point where schema objects are constructed.

Define deterministic property type fallback when source type is missing or unsupported.
Rationale: Prevent null or invalid YAML property types while keeping output stable and parseable.
Alternative considered: dropping unknown-typed properties. Rejected because it reintroduces metadata loss.

Add focused tests at service level for `inferSchema` behavior.
Rationale: The regression is in mapping logic; service-level tests are fast and directly validate preserved descriptions/properties.
Alternative considered: only end-to-end tests. Rejected because failures are harder to localize and slower to run.

## Risks / Trade-offs

- Type mapping mismatch between transformer property typing and schema typing conventions -> Mitigation: centralize conversion logic and cover it with tests for expected and fallback mappings.
- Duplicate node/edge definitions with conflicting metadata from multiple extracted documents -> Mitigation: preserve existing merge strategy and extend it to prefer non-blank descriptions and union properties.
- Slightly larger generated YAML payloads -> Mitigation: acceptable trade-off for schema completeness.
