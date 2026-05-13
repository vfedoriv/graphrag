## Context

`GET /api/v1/schemas/{schemaId}` currently returns schema metadata but omits persisted schema content, so callers cannot fully inspect a schema with a single read. The change is localized to schema retrieval response shaping and should preserve existing endpoint semantics, error handling, and compatibility for existing clients.

## Goals / Non-Goals

**Goals:**
- Include schema content in `SchemaController#getSchema()` output for successful schema lookups.
- Keep the existing retrieval flow and response fields unchanged, aside from adding the new content field.
- Cover the behavior with controller/service tests to prevent regressions.

**Non-Goals:**
- Changing schema persistence format or storage model.
- Changing list endpoints, schema generation endpoints, or schema mutation flows.
- Introducing versioned API path changes.

## Decisions

1. Add schema content to the single-schema response DTO used by `/schemas/{schemaId}`.
Rationale: this keeps the API contract explicit and avoids ad-hoc map-based responses.
Alternative considered: return a second endpoint for schema content retrieval. Rejected because it increases client round-trips and fragments read behavior.

2. Populate content from existing persisted schema entity/service mapping path.
Rationale: reuse current source of truth and avoid duplicate storage/joins.
Alternative considered: recompute or transform content on read. Rejected because schema content should be returned exactly as persisted.

3. Preserve backward compatibility by adding a non-breaking response field.
Rationale: additive JSON fields are safe for existing clients that ignore unknown properties.
Alternative considered: replace existing payload shape. Rejected due to unnecessary contract churn.

## Risks / Trade-offs

- [Risk] Larger payload size for large schema definitions. → Mitigation: limit change to single-schema endpoint; list endpoints remain lightweight.
- [Risk] Null/empty content edge cases for legacy records. → Mitigation: map content directly and keep consistent serialization; add tests for representative records.
- [Trade-off] More complete response vs. potentially higher response transfer cost. → Mitigation: keep endpoint purpose focused on full schema retrieval.
