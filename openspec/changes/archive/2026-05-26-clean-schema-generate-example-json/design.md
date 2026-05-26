## Context

`/api/v1/schemas/generate/example` and `/api/v1/schemas/generate/example/from-file` currently return an `example` string that may include leading newline escape sequences (for example `\n\n`) before JSON array content. Callers can still parse the string, but the payload is noisy and harder to reuse directly in downstream requests.

The change is scoped to response normalization only. Endpoint routing, request validation, and model invocation behavior remain unchanged.

## Goals / Non-Goals

**Goals:**
- Return normalized `example` text for both example generation endpoints.
- Remove leading/trailing formatting artifacts (including leading blank lines) from generated example content.
- Keep existing response contract shape (`{ "example": "<string>" }`) while improving output quality.
- Add automated test coverage for normalization behavior.

**Non-Goals:**
- Changing response field names or introducing a new response DTO contract.
- Persisting generated examples or schemas.
- Altering prompt semantics beyond what is required to normalize final output.

## Decisions

1. Normalize at application boundary before response serialization.
   - Decision: apply trimming/cleanup in the service/mapper layer that builds `example` responses.
   - Rationale: centralizes behavior for both text and file paths and avoids controller duplication.
   - Alternative considered: prompt-only fix to force clean output. Rejected because model output variability can still leak formatting artifacts.

2. Keep `example` as JSON text string rather than converting to structured object/array.
   - Decision: preserve existing API contract (`example: string`) while ensuring the string contains clean JSON content.
   - Rationale: minimizes client breakage and keeps change backward compatible for consumers expecting text.
   - Alternative considered: return parsed JSON array/object. Rejected as a broader contract change.

3. Validate with endpoint-level tests that assert formatting quality.
   - Decision: add/update tests to verify absence of leading escaped newlines and stable JSON-text output.
   - Rationale: captures regressions at observable API boundary.

## Risks / Trade-offs

- [Risk] Over-trimming could remove meaningful whitespace if model output is intentionally formatted. → Mitigation: limit normalization to leading/trailing whitespace cleanup without altering interior JSON content.
- [Risk] Some clients may depend on exact old raw format. → Mitigation: preserve field name and JSON-text semantics; communicate formatting cleanup in release notes/changelog if needed.
