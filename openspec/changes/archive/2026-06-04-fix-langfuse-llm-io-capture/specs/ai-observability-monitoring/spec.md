## ADDED Requirements

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
The system SHALL keep Langfuse-compatible input and output fields non-empty while respecting configured content capture and length limits.

#### Scenario: Full input and output capture is enabled
- **WHEN** AI observability is enabled with input/output content capture enabled
- **THEN** Langfuse-compatible input and output fields include prompt, input text, query, response text, or embedding summaries up to the configured input/output length limit

#### Scenario: Full input and output capture is disabled
- **WHEN** AI observability is enabled with input/output content capture disabled
- **THEN** Langfuse-compatible input and output fields include sanitized previews, lengths, and hashes instead of full prompt, query, input text, or response content
- **AND** those fields remain non-empty for successful internal model calls

#### Scenario: Input or output exceeds configured length
- **WHEN** a Langfuse-compatible input or output value is longer than the configured maximum input/output length
- **THEN** the exported value is truncated to the configured maximum

### Requirement: Missing Langfuse input and output coverage is testable
The system SHALL include automated regression coverage that verifies Langfuse-compatible input and output fields for centralized model paths.

#### Scenario: Centralized model path tests run
- **WHEN** observability tests exercise graph extraction, Cypher generation, schema generation, embedding, and model adapter paths with mocked model responses
- **THEN** the tests verify non-empty Langfuse-compatible input and output attributes on the model observation
- **AND** the tests verify trace-level input and output propagation when the call runs inside a workflow observation
