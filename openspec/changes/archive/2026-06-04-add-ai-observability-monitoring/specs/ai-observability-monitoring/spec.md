## ADDED Requirements

### Requirement: AI tracing can be enabled without changing API behavior
The system SHALL provide configurable AI observability that traces model-facing workflows without changing existing request or response contracts.

#### Scenario: Observability disabled
- **WHEN** the application starts with AI observability disabled
- **THEN** document processing, schema generation, graph extraction, embedding, and query workflows continue to behave as they did before the change

#### Scenario: Observability enabled
- **WHEN** the application starts with AI observability enabled and a valid OTLP exporter configuration
- **THEN** model-facing workflows emit traces to the configured OpenTelemetry backend

### Requirement: Model operations are traced with useful metadata
The system SHALL trace chat generation and embedding operations with operation names, model/provider metadata, latency, status, and usage details when available from the provider response.

#### Scenario: Chat call succeeds
- **WHEN** a chat model call completes successfully
- **THEN** the exported trace includes the workflow name, model name when known, provider endpoint profile when known, elapsed time, success status, and response metadata available from Spring AI

#### Scenario: Embedding call succeeds
- **WHEN** an embedding model call completes successfully
- **THEN** the exported trace includes the workflow name, model name when known, input batch size, vector count, vector dimension, elapsed time, success status, and usage metadata available from Spring AI

#### Scenario: Model call fails
- **WHEN** a chat or embedding call fails
- **THEN** the exported trace marks the operation as failed and records the exception type and sanitized error message

### Requirement: Dashboard-stable trace attributes are low-cardinality
The system SHALL define dashboard-stable trace attributes that are low-cardinality and safe for future alerts and dashboards.

#### Scenario: Stable attributes emitted
- **WHEN** a model-facing workflow emits a trace
- **THEN** the trace includes stable attributes for operation, workflow, provider profile when known, model name when known, status, failure category when failed, and content capture state

#### Scenario: Diagnostic identifiers remain trace-only
- **WHEN** a trace includes document id, knowledge base id, schema id, extraction run id, prompt hash, raw prompt, raw response, natural-language query, chunk text, or raw exception message
- **THEN** those attributes are not used as metric tags or documented as stable dashboard and alert dimensions

### Requirement: Business workflow spans correlate AI work
The system SHALL create parent spans for AI-assisted business workflows so lower-level model calls can be inspected in context.

#### Scenario: Document processing run
- **WHEN** a document is processed with an active schema
- **THEN** traces correlate parsing, chunking, embedding, graph extraction, and persistence work with document id, knowledge base id, schema id, and extraction run id when available

#### Scenario: Schema generation request
- **WHEN** a schema generation request invokes the model
- **THEN** traces correlate the model call with schema generation metadata such as example count and generated schema name when available

#### Scenario: Query generation request
- **WHEN** a natural-language query is converted to Cypher
- **THEN** traces correlate the model call with knowledge base id, active schema id, validation outcome, and execution status when available

### Requirement: Model operations emit lightweight metrics
The system SHALL emit Micrometer metrics for model call attempts, failures, latency, and token usage when usage metadata is available.

#### Scenario: Model call metrics recorded
- **WHEN** a chat or embedding model call completes
- **THEN** Micrometer metrics record the call count, latency, operation, workflow, provider profile when known, model name when known, and success or failure status

#### Scenario: Token usage metadata exists
- **WHEN** a model response includes input, output, or total token usage metadata
- **THEN** Micrometer metrics record the available token counts without inventing missing values

#### Scenario: Metrics avoid high-cardinality tags
- **WHEN** model metrics are emitted
- **THEN** metric tags exclude document ids, knowledge base ids, schema ids, extraction run ids, prompt text, response text, prompt hashes, chunk text, raw exception messages, and natural-language query values

### Requirement: Prompt and response capture is configurable
The system SHALL export full Langfuse-compatible trace input and output content by default for AI debugging, SHALL provide configuration to disable that behavior, and SHALL provide a separate maximum length for exported trace input and output content.

#### Scenario: Input and output content capture enabled
- **WHEN** AI observability is enabled with input and output content capture enabled
- **THEN** Langfuse-compatible trace input and output fields include full prompt, query, or model response content up to the configured input/output length limit

#### Scenario: Input and output content capture disabled
- **WHEN** AI observability is enabled with input and output content capture disabled
- **THEN** Langfuse-compatible trace input and output fields include sanitized previews, lengths, and hashes instead of full prompt, query, and model response content

#### Scenario: Generic content capture disabled
- **WHEN** AI observability is enabled with generic content capture disabled
- **THEN** non-Langfuse trace metadata includes sanitized previews, lengths, hashes, and structured metadata instead of separate full content attributes

### Requirement: Langfuse is available as an optional local backend
The system SHALL provide a Docker Compose profile that runs a self-hosted Langfuse backend suitable for local development.

#### Scenario: Default compose startup
- **WHEN** `docker compose up -d neo4j` or the default application workflow is used
- **THEN** Langfuse services are not started automatically

#### Scenario: Langfuse profile startup
- **WHEN** the Langfuse compose profile is selected
- **THEN** the compose stack starts Langfuse web, Langfuse worker, Postgres, ClickHouse, Redis, and object storage services with persistent volumes and health checks

#### Scenario: Local Langfuse project initialized
- **WHEN** the Langfuse compose profile starts with local-development defaults
- **THEN** Langfuse initializes a development organization, project, user, public key, and secret key from environment variables

#### Scenario: Application exports to Langfuse
- **WHEN** the application is configured with Langfuse OTLP endpoint and authentication headers
- **THEN** traces are exported to Langfuse using OpenTelemetry ingestion

### Requirement: Observability setup is documented
The system SHALL document how to enable local Langfuse monitoring, use the auto-created local development project and keys, configure OTLP export, handle credentials, control content capture, and verify that AI traces are received.

#### Scenario: Developer follows documentation
- **WHEN** a developer follows the documented local setup using an AI-enabled profile
- **THEN** they can open the Langfuse UI and inspect traces for at least one chat or embedding workflow

#### Scenario: Developer reads credential guidance
- **WHEN** a developer reads the observability setup documentation
- **THEN** the documentation identifies auto-created Langfuse credentials as local-development defaults that must not be reused in shared or production environments
