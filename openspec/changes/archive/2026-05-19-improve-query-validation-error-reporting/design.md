## Context

`/api/v1/knowledge-bases/{knowledgeBaseId}/queries/execute` currently rejects invalid Cypher with `QueryRejectedException: Query validation failed` and limited operator-facing context. Validation already computes detailed error messages (`errorCount` and message list), but rejection responses and top-level logs do not consistently surface them.

## Goals / Non-Goals

**Goals:**
- Include validation error text in rejection responses for query execution.
- Improve rejection logs to include sanitized error details with current metadata (knowledge base id, count).
- Preserve existing validation rules and rejection decision semantics.

**Non-Goals:**
- No change to validation rule set or parser behavior.
- No change to successful query response contracts.
- No change to HTTP route shape.

## Decisions

1. Extend rejection payload with validation error list.
Reasoning: clients need machine- and human-readable failure details to correct queries quickly.
Alternative considered: keep details only in logs; rejected because client UX remains poor.

2. Keep generic summary message and append detailed list.
Reasoning: preserves compatibility for clients that key off the top-level message while adding richer details.
Alternative considered: replacing summary with first detailed message only; rejected due to lost context for multi-error cases.

3. Log sanitized validation detail summary at rejection boundary.
Reasoning: operators should see concrete failure reasons without parsing stack traces.
Alternative considered: debug-level only details; rejected because production troubleshooting would still be opaque.

4. Reuse existing exception/ProblemDetail path.
Reasoning: minimizes cross-cutting changes and keeps error handling centralized.
Alternative considered: custom response DTO bypassing global exception handler; rejected due to duplication.

## Risks / Trade-offs

- [Risk] Response payload change may affect strict client deserializers. → Mitigation: additive field only, keep existing summary message and status.
- [Risk] Verbose validation output may leak sensitive text. → Mitigation: apply existing log sanitization and avoid raw query dumps in new log fields.
- [Risk] Multi-error payload size growth. → Mitigation: return validation messages only (already bounded by validation flow).

## Migration Plan

1. Update `QueryRejectedException` (or equivalent error carrier) to include validation message list.
2. Update `CypherExecutionService` to pass validation errors into exception and log summary.
3. Update global exception mapping to include error details in response body.
4. Add/adjust unit and controller integration tests for single and multiple validation errors.
5. Verify no regression for successful query execution paths.

## Open Questions

- Should the response field be named `errors`, `validationErrors`, or use RFC7807 `extensions` key conventions already used in this API?
- Do we want a hard max on returned validation errors (if future validators return many)?
