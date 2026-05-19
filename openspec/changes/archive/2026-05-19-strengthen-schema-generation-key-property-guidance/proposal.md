## Why

LLM-generated schemas sometimes return node `key` values that are not present in the node `properties` list, causing confusion and downstream validation failures. We need generation-time guidance and post-generation advisory checks so users get actionable feedback before persistence-time rejection.

## What Changes

- Strengthen schema-generation prompts with explicit constraints that each node `key` must reference one of that node's property names.
- Add post-generation advisory validation for key/property mismatches that emits warnings/suggestions (not hard failure) in generation responses.
- Include per-node suggestions to fix mismatches (e.g., add missing key property or change `key` to a declared property).
- Preserve existing strict schema creation validation behavior; this change improves generation-time diagnostics only.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `schema-generation-with-examples`: generation output quality constraints and advisory validation feedback are expanded.

## Impact

- Affected code: schema generation service/client prompt builder, generated schema response DTO, and generation controller/service tests.
- API impact: generation response may include warnings/suggestions for key/property mismatches.
- Operational impact: fewer confusing schema create failures because users see guidance earlier in generation flow.
