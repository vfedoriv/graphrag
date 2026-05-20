## Why

Knowledge base behavior depends on a single active schema, but activation currently does not enforce exclusivity at the knowledge-base scope. This can create ambiguous query and extraction behavior when multiple schemas appear active for the same knowledge base.

## What Changes

- Enforce that at most one schema can be active within a given knowledge base at any time.
- When a schema is activated, automatically deactivate all other schemas in the same knowledge base.
- Keep activation scoped to the target knowledge base so schemas in other knowledge bases are unaffected.
- Define deterministic behavior for repeated activation of an already active schema.

## Capabilities

### New Capabilities
- `single-active-schema-per-knowledge-base`: Enforce exclusive active schema selection per knowledge base and automatic deactivation of sibling schemas on activation.

### Modified Capabilities
- `cypher-validation`: Clarify that the active schema reference is unambiguous because only one active schema can exist per knowledge base.

## Impact

- Affected services: schema activation flows in schema registry and any code paths that resolve active schema by knowledge base.
- Affected persistence behavior: schema activation updates must include deactivation of sibling schemas in the same knowledge base.
- Affected API behavior: activating a schema may now update multiple schema records (target activation plus sibling deactivations) while preserving current endpoint contract.
- Affected tests: integration and repository tests for activation behavior and active schema resolution.
