## Context

`QueryController.ask` currently performs the full one-shot query workflow: starts the query observation, generates Cypher, checks validation, executes the query, records attributes, and assembles the response. This makes the API layer responsible for application workflow sequencing and observability. The controller should only adapt HTTP input/output.

## Goals / Non-Goals

**Goals:**
- Move the one-shot ask workflow into an application service.
- Preserve `/queries/ask` response structure, validation rejection behavior, and observability attributes.
- Keep generate, validate, and execute services reusable as independent operations.
- Make the controller thin and easy to test.

**Non-Goals:**
- Changing Cypher generation prompts.
- Changing query validation rules.
- Changing query execution result normalization.
- Adding streaming or chat history.

## Decisions

- Introduce `QueryAskService`.
  - Rationale: asking a natural language question is a first-class use case composed from generation and execution.
- Keep `CypherGenerationService` responsible for generation plus validation of generated Cypher.
  - Rationale: it already returns `GeneratedQueryResponse` with validation details.
- Keep `CypherExecutionService` responsible for revalidation and execution.
  - Rationale: execution remains protected even if called directly.
- Move query workflow observation from `QueryController` to `QueryAskService`.
  - Rationale: observation belongs to the use case rather than HTTP transport.

## Risks / Trade-offs

- Double validation remains in ask flow -> Accept for safety; changing this is outside scope.
- Tests may need updated mocking boundaries -> Add focused `QueryAskServiceTest` and keep controller tests verifying delegation.
- Observability regression -> Preserve existing workflow name and attributes in tests.
