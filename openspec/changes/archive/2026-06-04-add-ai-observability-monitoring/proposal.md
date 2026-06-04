## Why

LLM calls currently surface only as application logs and persisted domain outcomes, which makes it hard to debug prompt behavior, latency, token usage, provider failures, and multi-step AI workflows across document ingestion, schema generation, graph extraction, embeddings, and Cypher generation. Adding comprehensive AI observability now will make production and local AI runs inspectable without changing the read/write API contracts.

## What Changes

- Add optional AI observability for all model-facing workflows, including chat generations, embedding calls, schema generation, graph extraction, and Cypher generation.
- Use OpenTelemetry as the application tracing contract so Spring AI instrumentation and explicit application spans can export to Langfuse or another OTLP-compatible backend.
- Add a Langfuse self-hosting option to Docker Compose behind an explicit profile, including the required web, worker, Postgres, ClickHouse, Redis, and object storage services.
- Add configuration for enabling/disabling observability, setting the OTLP endpoint and headers, capturing useful Langfuse input/output by default, and disabling or limiting full content export when needed.
- Document local setup, credential handling, prompt/content capture controls, and verification steps.

## Capabilities

### New Capabilities
- `ai-observability-monitoring`: Covers tracing and monitoring of LLM and embedding operations, agent-like workflow spans, metadata propagation, privacy controls, and optional Langfuse self-hosted export.

### Modified Capabilities

## Impact

- Affected code: Spring AI chat and embedding clients, LangChain4j adapter usage, graph extraction, schema generation, Cypher generation, document processing, application configuration, and tests around model client behavior.
- Affected dependencies: OpenTelemetry instrumentation/exporter and Micrometer tracing bridge dependencies may be added; Langfuse is consumed through OTLP rather than as a hard application SDK dependency.
- Affected infrastructure: `compose.yaml` gains optional Langfuse services and persistent volumes; default Neo4j-only startup remains unchanged unless the Langfuse profile is selected.
- Affected docs: README and profile guidance gain observability setup and privacy notes.
