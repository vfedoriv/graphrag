## 1. Dependencies and Configuration

- [x] 1.1 Add OpenTelemetry, Micrometer tracing bridge, and OTLP exporter dependencies needed for Spring Boot/Spring AI tracing.
- [x] 1.2 Add typed application properties for AI observability enablement, content capture, stable service metadata, and optional trace attribute limits.
- [x] 1.3 Define low-cardinality stable trace attributes and Micrometer metric tags for operation, workflow, provider profile, bounded model name, status, failure category, and content capture state.
- [x] 1.4 Add profile/property defaults so observability is disabled or no-op unless explicitly configured.
- [x] 1.5 Add tests proving default profile behavior does not require an OTLP endpoint or Langfuse services.

## 2. Observation Abstraction

- [x] 2.1 Implement an `AiObservationService` facade for parent spans, model operation spans, Micrometer metrics, sanitized attributes, content capture decisions, and error recording.
- [x] 2.2 Reuse existing log sanitization behavior for prompt/response previews and add hashing or length metadata where useful.
- [x] 2.3 Add helper logic for recording model call counters, failure counters, latency timers, and token usage counters only when usage metadata exists.
- [x] 2.4 Add unit tests for disabled mode, enabled mode, content capture disabled, content capture enabled, stable trace attributes, metric recording, high-cardinality tag avoidance, and exception recording.

## 3. AI Workflow Instrumentation

- [x] 3.1 Instrument `SpringAiEmbeddingClient` with embedding operation spans, batch/vector metadata, elapsed time, provider metadata when available, and failure status.
- [x] 3.2 Instrument `SpringAiGraphExtractionClient` with chat generation spans and graph extraction metadata such as schema name, chunk length, node count, and relationship count.
- [x] 3.3 Instrument `SpringAiCypherGenerationClient` with chat generation spans and Cypher generation metadata such as schema name, request length, generated Cypher length, and parameter count.
- [x] 3.4 Instrument `SpringAiLangChain4jChatModelAdapter` so LangChain4j-backed schema generation routed through Spring AI emits model spans.
- [x] 3.5 Add parent workflow spans around document processing, graph extraction runs, schema generation, and query orchestration with document, knowledge base, schema, and run identifiers when available.
- [x] 3.6 Record Micrometer metrics from each centralized model path for call count, failure count, latency, and token usage when provider metadata exposes token counts.
- [x] 3.7 Add focused tests using mocked AI clients to verify spans and metrics are created without changing existing service outcomes.

## 4. Langfuse Local Backend

- [x] 4.1 Extend `compose.yaml` with a `langfuse` profile containing Langfuse web, Langfuse worker, Postgres, ClickHouse, Redis, and MinIO services.
- [x] 4.2 Add health checks, localhost-bound ports where practical, and persistent volumes for Langfuse storage services.
- [x] 4.3 Configure local-development environment variables that initialize a Langfuse organization, project, user, public key, and secret key.
- [x] 4.4 Add local-development environment variable defaults and comments or docs requiring real secrets outside local development.
- [x] 4.5 Verify default Neo4j startup remains unchanged when the `langfuse` profile is not selected.

## 5. Documentation and Verification

- [x] 5.1 Update README or profile documentation with local Langfuse startup, auto-created local project/API keys, OTLP endpoint, OTLP headers, and run commands.
- [x] 5.2 Document content capture privacy behavior, default Langfuse input/output capture, and how to disable or length-limit full prompt/response capture.
- [x] 5.3 Document that local Langfuse defaults are development-only and must not be reused in shared or production environments.
- [x] 5.4 Run `./mvnw test` and fix regressions.
- [x] 5.5 Manually verify an AI-enabled local profile exports at least one chat or embedding trace to the auto-created Langfuse project and record the verification steps in documentation.
