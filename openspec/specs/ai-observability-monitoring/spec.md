# ai-observability-monitoring Specification

## Purpose
TBD - created by archiving change fix-langfuse-llm-io-capture. Update Purpose after archive.
## Requirements
### Requirement: Langfuse input and output are populated for internal LLM calls
The system SHALL populate non-empty Langfuse-compatible input and output fields for every internal LLM call when AI observability is enabled and the call reaches the model provider.

#### Scenario: Graph extraction chat call succeeds
- **WHEN** graph extraction invokes the chat model and receives a response
- **THEN** the exported model observation includes non-empty `langfuse.observation.input` and `langfuse.observation.output` values
- **AND** the enclosing trace includes non-empty `langfuse.trace.input` and `langfuse.trace.output` values when a workflow/root span exists

#### Scenario: Cypher generation chat call succeeds
- **WHEN** Cypher generation invokes the chat model and receives a response
- **THEN** the exported model observation includes non-empty `langfuse.observation.input` and `langfuse.observation.output` values
- **AND** the enclosing trace includes non-empty `langfuse.trace.input` and `langfuse.trace.output` values when a workflow/root span exists

#### Scenario: Schema generation chat call succeeds
- **WHEN** schema generation invokes the chat model and receives a response
- **THEN** the exported model observation includes non-empty `langfuse.observation.input` and `langfuse.observation.output` values
- **AND** the enclosing trace includes non-empty `langfuse.trace.input` and `langfuse.trace.output` values when a workflow/root span exists

#### Scenario: Embedding call succeeds
- **WHEN** document processing or another internal workflow invokes the embedding model and receives vectors
- **THEN** the exported model observation includes non-empty `langfuse.observation.input` and `langfuse.observation.output` values
- **AND** the output value summarizes the embedding result without exporting raw vectors

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

### Requirement: Missing Langfuse input and output coverage is testable
The system SHALL include automated regression coverage that verifies Langfuse-compatible input and output fields for centralized model paths.

#### Scenario: Centralized model path tests run
- **WHEN** observability tests exercise graph extraction, Cypher generation, schema generation, embedding, and model adapter paths with mocked model responses
- **THEN** the tests verify non-empty Langfuse-compatible input and output attributes on the model observation
- **AND** the tests verify trace-level input and output propagation when the call runs inside a workflow observation

### Requirement: AI observations include active profile metadata when enabled
The system SHALL use active AI profile metadata for model/provider observation tags when model-name and provider tags are enabled.

#### Scenario: Knowledge-base workflow uses active profile
- **WHEN** a knowledge-base-scoped AI workflow emits model observation metadata
- **THEN** the provider/profile metadata reflects the active AI profile used by that workflow
- **AND** the model name metadata reflects the chat or embedding model used for that call when model name tags are enabled

#### Scenario: Model name tags are disabled at runtime
- **WHEN** runtime settings disable model name tags
- **THEN** subsequent model observations do not expose active profile model names as model tags

### Requirement: Application logs do not mirror AI observation content
The system SHALL keep application logging independent from AI observation input/output capture settings.

#### Scenario: AI content capture is enabled
- **WHEN** AI observability content capture is enabled for a model call
- **THEN** the centralized observation contains content according to its configured privacy and length limits
- **AND** normal application logs still contain only metadata and observation identifiers

#### Scenario: AI content capture is disabled
- **WHEN** AI observability content capture is disabled for a model call
- **THEN** the observation uses its configured sanitized representation
- **AND** application logs do not independently expose the input or output content
