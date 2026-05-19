## Why

When query validation fails, operators and API clients only see a generic `Query validation failed` message, while actionable details stay buried or omitted. This slows debugging and makes it hard for users to fix invalid Cypher quickly.

## What Changes

- Return structured validation error details in query execution failure responses instead of only a generic message.
- Ensure every validation rejection includes all validation error texts gathered during the validation step.
- Improve rejection logging so logs include a concise, sanitized summary of validation error messages.
- Keep existing read-only validation and rejection behavior unchanged; only diagnostics and error payload clarity change.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `cypher-validation`: Expand rejection diagnostics to require explicit validation error messages in response payloads and logs.

## Impact

- Affected code: `CypherExecutionService`, `QueryController` error path, `QueryRejectedException`, `GlobalExceptionHandler`, and related tests.
- API impact: query execution error responses become more descriptive by including validation error message text.
- Operational impact: logs provide clearer rejection reasons without requiring deep stack-trace inspection.
