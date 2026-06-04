## 1. Investigation

- [x] 1.1 Reproduce or inspect current trace attribute propagation for schema example generation, graph extraction, Cypher generation, schema generation, and embedding paths.
- [x] 1.2 Identify which paths attach non-empty Langfuse input/output only to child model spans, only to parent spans, or not at all.

## 2. Core Implementation

- [x] 2.1 Update `AiObservationService` and `AiModelCallObservation` so Langfuse input/output attributes propagate consistently to model observations and active workflow/root spans.
- [x] 2.2 Ensure every chat model path supplies prompt input and response output through the centralized Langfuse input/output helpers.
- [x] 2.3 Ensure embedding model paths supply text input and a bounded non-vector output summary through the centralized Langfuse input/output helpers.
- [x] 2.4 Preserve existing content capture and max input/output length behavior for full content, preview/hash fallback, and truncation.

## 3. Tests

- [x] 3.1 Add or update observability unit tests for non-empty model observation input/output attributes.
- [x] 3.2 Add or update tests for trace-level input/output propagation when model calls run inside workflow observations.
- [x] 3.3 Add or update tests covering content capture disabled and max input/output length truncation.
- [x] 3.4 Add path-level regression coverage for graph extraction, Cypher generation, schema generation, embedding, and the LangChain4j chat adapter using mocked model responses.

## 4. Verification

- [x] 4.1 Run the focused observability and LLM client tests.
- [x] 4.2 Run `./mvnw test`.
- [x] 4.3 Verify locally with the `lm_studio,langfuse` profiles that representative internal LLM calls show non-empty input/output fields in Langfuse.
