# RFC 7807 error model

API failures use `application/problem+json` Spring `ProblemDetail`. Clients should branch on HTTP status and machine-readable extensions where present, while displaying `detail` as operator context rather than parsing prose.

Representative shape:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "The requested operation conflicts with current resource state.",
  "instance": "/api/v1/example",
  "errors": []
}
```

Not every response has `errors`; workflow-specific failures may add blockers, reason IDs, validation details, or retry hints.

## Common status classes

| Status | Typical causes | Client action |
|---|---|---|
| 400 | Bean validation, malformed multipart/JSON, invalid schema, unsafe Cypher, invalid page/filter/options | Correct the request; do not retry unchanged. |
| 404 | Unknown/foreign knowledge base, schema, document, chunk, draft, run, plan, or profile | Refresh ownership-scoped state/URL. |
| 409 | Stale revision, active-schema mutation, overwrite guard, embedding incompatibility, readiness blocker, changed migration target/source | Read current state/blockers and make the explicit next decision. |
| 413 | Multipart upload exceeds configured limits | Reduce/split input or change deployment limit. |
| 429 / 503 | Bounded queue/admission or provider/service capacity | Respect retry guidance/backoff; verify no durable resource was created. |
| 500 | Unexpected persistence, storage, model, or orchestration failure | Correlate safe metadata, inspect durable state, and retry only when workflow semantics allow. |

## Concurrency and durable work

Schema-draft mutations carry revisions; stale writes return 409. Advanced-search readiness failures happen before a run is created. Draft analysis/evaluation and reprocessing return durable resources only after admission succeeds. Cancellation and retry are explicit and idempotent/linked where documented.

For a terminal durable failure, inspect `failureCategory`, `retryable`/`canRetry`, item/source outcomes, limitations, and diagnostics. Do not infer retryability from HTTP status alone.

## Privacy

Problem details must not echo document text, prompts, queries, model responses, secrets, generated schemas, or graph payloads. Correlate via resource IDs, status, safe fingerprints, and trace policy described in [observability](../operations/observability.md).

Implementation: `GlobalExceptionHandler` and workflow-specific exception types. Runtime OpenAPI shows the exact response extensions for each endpoint.
