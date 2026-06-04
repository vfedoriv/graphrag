## Why

Langfuse traces currently show useful `input` and `output` fields for `/api/v1/schemas/generate/example`, but other internal LLM workflows can appear with empty input/output fields even though they use chat or embedding models. This makes graph extraction, Cypher generation, document processing, and other AI-assisted paths hard to debug in Langfuse when prompt or response behavior matters.

## What Changes

- Ensure every internal LLM call that uses the configured chat or embedding model exports non-empty Langfuse-compatible input and output fields when AI observability is enabled.
- Populate the Langfuse observation-level fields for the model operation and the trace-level fields for the surrounding workflow/root span where applicable.
- Preserve existing privacy controls: full content capture remains governed by `app.ai.observability.input-output-content-enabled`, and exported values remain capped by `app.ai.observability.max-input-output-length`.
- Add regression coverage for graph extraction, Cypher generation, schema generation, embedding, and any centralized model adapter paths so missing input/output attributes are caught.
- Keep public API behavior unchanged.

## Capabilities

### New Capabilities

- `ai-observability-monitoring`: AI model calls and model-facing workflows emit Langfuse-compatible input/output tracing fields consistently.

### Modified Capabilities

- None.

## Impact

- Affected code: `AiObservationService`, `AiModelCallObservation`, centralized LLM client/adapters, workflow span call sites, and observability tests.
- Affected systems: Langfuse/OpenTelemetry trace export only.
- API impact: no request or response contract changes.
- Dependency impact: no new dependency expected.
