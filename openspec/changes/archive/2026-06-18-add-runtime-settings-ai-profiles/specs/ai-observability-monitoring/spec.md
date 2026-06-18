## MODIFIED Requirements

### Requirement: Langfuse input and output respect content privacy controls
The system SHALL keep Langfuse-compatible input and output fields non-empty while respecting runtime-configured content capture and length limits.

#### Scenario: Full input and output capture is enabled
- **WHEN** AI observability is enabled with input/output content capture enabled through startup or runtime settings
- **THEN** Langfuse-compatible input and output fields include prompt, input text, query, response text, or embedding summaries up to the configured input/output length limit

#### Scenario: Full input and output capture is disabled
- **WHEN** AI observability is enabled with input/output content capture disabled through startup or runtime settings
- **THEN** Langfuse-compatible input and output fields include sanitized previews, lengths, and hashes instead of full prompt, query, input text, or response content
- **AND** those fields remain non-empty for successful internal model calls

#### Scenario: Input or output exceeds configured length
- **WHEN** a Langfuse-compatible input or output value is longer than the runtime-configured maximum input/output length
- **THEN** the exported value is truncated to the configured maximum

## ADDED Requirements

### Requirement: AI observations include active profile metadata when enabled
The system SHALL use active AI profile metadata for model/provider observation tags when model-name and provider tags are enabled.

#### Scenario: Knowledge-base workflow uses active profile
- **WHEN** a knowledge-base-scoped AI workflow emits model observation metadata
- **THEN** the provider/profile metadata reflects the active AI profile used by that workflow
- **AND** the model name metadata reflects the chat or embedding model used for that call when model name tags are enabled

#### Scenario: Model name tags are disabled at runtime
- **WHEN** runtime settings disable model name tags
- **THEN** subsequent model observations do not expose active profile model names as model tags
