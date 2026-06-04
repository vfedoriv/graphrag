## Context

AI observability is implemented through `AiObservationService` and `AiModelCallObservation`, with model-facing clients adding prompt and response metadata through `langfuseInputAttributes` and `langfuseOutputAttributes`. The current code uses the Langfuse-recognized OpenTelemetry keys `langfuse.observation.input`, `langfuse.observation.output`, `langfuse.trace.input`, `langfuse.trace.output`, `input.value`, and `output.value`.

Langfuse documentation confirms that observation input/output maps reliably from `langfuse.observation.input` and `langfuse.observation.output`, while trace input/output maps from `langfuse.trace.input` and `langfuse.trace.output` or the root span's observation input/output. The reported behavior suggests that at least one model path either does not attach non-empty prompt/response values at the final exported span, or attaches them only to a span Langfuse does not treat as the trace/root field source.

The implementation must keep existing public APIs unchanged and preserve the existing privacy switches for full content capture and length limits.

## Goals / Non-Goals

**Goals:**

- Make Langfuse input/output fields non-empty for every internal chat model call.
- Make embedding model calls expose useful input/output summaries when full vector output is not appropriate.
- Ensure model operation spans include observation-level input/output and workflow/root spans include trace-level input/output when a parent workflow exists.
- Keep full content capture configurable and length-capped.
- Add regression tests that fail if any centralized model path omits Langfuse-compatible input/output attributes.

**Non-Goals:**

- Change request or response payloads for API endpoints.
- Add a Langfuse SDK dependency or replace the OpenTelemetry export path.
- Export raw embedding vectors as Langfuse output content.
- Introduce prompt management, evaluations, cost dashboards, or other Langfuse features.

## Decisions

### Centralize Langfuse input/output propagation

Keep the recognized Langfuse/OpenTelemetry attribute keys in `AiObservationService`, and make propagation explicit for both model observations and parent workflow/root spans. Model call start should attach input attributes to the model observation and current active span. Later output attributes should be attached through `AiModelCallObservation` so the child model span, parent workflow observation, parent span, and active span are updated consistently.

Alternative considered: fix each LLM client independently. Rejected because the same missed propagation issue can recur when new model clients are added.

### Treat chat and embedding outputs differently

Chat outputs should use the model response text. Embedding outputs should use a bounded structured summary, such as vector count, dimensions, and provider metadata, instead of raw vectors. Embedding inputs can include the batched text content when input/output capture is enabled, or preview/hash metadata when disabled.

Alternative considered: export raw embedding vectors. Rejected because vectors are large, hard to inspect, and can leak source text characteristics without improving Langfuse debugging.

### Preserve privacy configuration semantics

`app.ai.observability.input-output-content-enabled=false` must still produce non-empty Langfuse input/output values, but those values must be previews plus length/hash metadata instead of full prompt, query, or response content. The max input/output length cap remains the final guardrail for all Langfuse-compatible content fields.

Alternative considered: leave fields empty when content capture is disabled. Rejected because the existing behavior promises diagnostic previews and hashes, and empty fields make trace verification ambiguous.

### Add tests around exported attributes, not Langfuse UI behavior

Regression tests should inspect observation/span attributes produced by the application code. They should verify that each centralized model client or adapter adds non-empty values for `langfuse.observation.input`, `langfuse.observation.output`, `langfuse.trace.input`, and `langfuse.trace.output` where applicable.

Alternative considered: add a live Langfuse integration test. Rejected for the main test suite because it would require an external stack and credentials.

## Risks / Trade-offs

- [Risk] Multiple model calls inside one workflow can overwrite trace-level input/output on the parent span. -> Mitigation: accept last-writer behavior for this fix, and keep per-call details on model observation spans where each call has its own input/output.
- [Risk] Embedding input batches can be large. -> Mitigation: apply the existing input/output length cap and content-capture toggle.
- [Risk] Some provider responses may lack token or metadata fields. -> Mitigation: keep output content independent from usage metadata and avoid inventing missing usage values.
- [Risk] Langfuse mapping behavior may change. -> Mitigation: continue using Langfuse's documented `langfuse.observation.*` and `langfuse.trace.*` attributes rather than only generic OTel keys.
