## Why

The current schema authoring and generation flow is YAML-based, but the product direction is to standardize all schema definitions on JSON for consistency with ecosystem tooling and API payload expectations. This migration is needed now to remove dual-format ambiguity and enforce a single, explicit contract.

## What Changes

- **BREAKING**: Replace YAML schema input/output with JSON-only behavior across schema creation, validation, generation, bootstrap loading, and documentation.
- **BREAKING**: Persist newly created schemas with `SchemaFormat.JSON` and treat persisted YAML schema records as incompatible with the upgraded runtime.
- Update schema bootstrap resources and fixtures from `*.yaml` to `*.json`.
- Update API docs, examples, and tests to reference JSON content and JSON format values.
- Remove runtime YAML parsing support and related YAML-specific dependency usage where no longer needed.

## Capabilities

### New Capabilities
- `schema-json-format-enforcement`: enforce JSON as the only supported schema definition format for schema lifecycle operations and startup compatibility checks.

### Modified Capabilities
- `schema-generation-with-examples`: generated schema artifacts and behavior are updated from YAML responses/content to JSON responses/content.

## Impact

- Affected code: schema parser/validator, schema registry and bootstrap services, schema generation flow, controllers, DTO examples, and integration tests.
- Affected artifacts: bootstrap schemas under `src/main/resources/schemas`, test fixtures, and OpenSpec requirements.
- API impact: schema-related endpoints accept/return JSON schema text instead of YAML; format metadata for new schemas is `JSON`.
- Data/runtime impact: existing persisted YAML schemas require cleanup or migration before upgrade because runtime will no longer accept YAML as active schema content.
