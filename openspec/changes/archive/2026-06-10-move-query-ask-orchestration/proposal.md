## Why

The `/queries/ask` endpoint currently coordinates the end-to-end generate, validate, execute, and observation workflow in the controller. That makes HTTP handling responsible for application orchestration and spreads query workflow concerns across the API layer.

## What Changes

- Add an application service that owns the one-shot ask workflow.
- Move AI workflow observation, generated-query validation handling, query execution, and `QueryAskResponse` assembly out of `QueryController`.
- Keep the public `/api/v1/knowledge-bases/{knowledgeBaseId}/queries/ask` response and error behavior unchanged.
- Keep standalone generate, validate, and execute endpoints unchanged.

## Capabilities

### New Capabilities
- `query-ask-orchestration`: Defines controller-independent orchestration for the one-shot natural-language query workflow.

### Modified Capabilities

## Impact

- Affected controller: `QueryController`.
- Affected services: new ask workflow service plus existing `CypherGenerationService` and `CypherExecutionService`.
- Affected observability: query workflow spans and high-cardinality attributes remain present but move to the application service.
- No API contract changes are intended.
